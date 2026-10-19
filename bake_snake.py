"""Bake the supplied glTF skin/weights into interpolated vertex frames (no runtime dependency).
Run: python bake_snake.py snake.gltf snake.bbmodel snake_frames.gz
Requires numpy/scipy only for authoring. Original assets are preserved separately.
"""
import base64
import gzip
import json
import struct
import sys
from pathlib import Path
import numpy as np
from scipy.spatial.transform import Rotation, Slerp


class Scene:
    def __init__(self, path):
        self.g = json.loads(Path(path).read_text())
        self.buffers = [base64.b64decode(b['uri'].split(',', 1)[1]) for b in self.g['buffers']]
        self.parent = {c: i for i, n in enumerate(self.g['nodes']) for c in n.get('children', [])}
        self.meshes = []
        for node in self.g['nodes']:
            if 'mesh' not in node:
                continue
            skin = self.g['skins'][node['skin']]
            inverse = self.read(skin['inverseBindMatrices']).reshape(-1, 4, 4).transpose(0, 2, 1)
            for p in self.g['meshes'][node['mesh']]['primitives']:
                assert p.get('mode', 4) == 4
                a = {k: self.read(v) for k, v in p['attributes'].items()}
                self.meshes.append((a, self.read(p['indices']).ravel(), skin['joints'], inverse))

    def read(self, i):
        a = self.g['accessors'][i]
        v = self.g['bufferViews'][a['bufferView']]
        types = {5120: 'i1', 5121: 'u1', 5122: '<i2', 5123: '<u2', 5125: '<u4', 5126: '<f4'}
        dtype = np.dtype(types[a['componentType']])
        width = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}[a['type']]
        return np.ndarray((a['count'], width), dtype=dtype, buffer=self.buffers[v['buffer']],
                          offset=v.get('byteOffset', 0) + a.get('byteOffset', 0),
                          strides=(v.get('byteStride', width * dtype.itemsize), dtype.itemsize)).copy()

    def globals(self, animation=None, time=0):
        nodes = self.g['nodes']
        values = [{k: np.array(n.get(k, default), dtype=float) for k, default in
                   [('translation', [0, 0, 0]), ('rotation', [0, 0, 0, 1]), ('scale', [1, 1, 1])]} for n in nodes]
        if animation:
            for c in animation['channels']:
                s = animation['samplers'][c['sampler']]
                assert s.get('interpolation', 'LINEAR') == 'LINEAR'
                times, data = self.read(s['input']).ravel(), self.read(s['output'])
                t = np.clip(time, times[0], times[-1])
                key = c['target']['path']
                if len(times) == 1:
                    value = data[0]
                elif key == 'rotation':
                    value = Slerp(times, Rotation.from_quat(data))(t).as_quat()
                else:
                    value = np.array([np.interp(t, times, data[:, j]) for j in range(data.shape[1])])
                values[c['target']['node']][key] = value
        result = {}
        def visit(i):
            if i not in result:
                v = values[i]
                local = np.eye(4)
                local[:3, :3] = Rotation.from_quat(v['rotation']).as_matrix() @ np.diag(v['scale'])
                local[:3, 3] = v['translation']
                if 'matrix' in nodes[i]:
                    local = np.array(nodes[i]['matrix']).reshape(4, 4).T
                result[i] = visit(self.parent[i]) @ local if i in self.parent else local
            return result[i]
        return [visit(i) for i in range(len(nodes))]

    def frame(self, animation=None, time=0):
        transforms = self.globals(animation, time)
        frames = []
        for a, indices, joints, inverse in self.meshes:
            palette = np.array([transforms[j] @ inv for j, inv in zip(joints, inverse)])
            weights = a['WEIGHTS_0'].astype(float)
            totals = weights.sum(axis=1)
            unweighted = totals == 0
            assert np.allclose(totals[~unweighted], 1, atol=1e-5)
            weighted = (palette[a['JOINTS_0']] * weights[:, :, None, None]).sum(axis=1)
            # Six exported vertices have no assigned bone. Keep them in the mesh bind space,
            # matching Blockbench's unweighted geometry rather than collapsing to the origin.
            weighted[unweighted] = np.eye(4)
            pos = np.column_stack([a['POSITION'], np.ones(len(weights))])
            pos = np.einsum('nij,nj->ni', weighted, pos)[:, :3]
            normal = np.einsum('nij,nj->ni', np.linalg.inv(weighted[:, :3, :3]).transpose(0, 2, 1), a['NORMAL'])
            normal /= np.linalg.norm(normal, axis=1)[:, None]
            frames.append(np.column_stack([pos, normal]))
        frame = np.concatenate(frames)
        assert np.isfinite(frame).all()
        return frame


def bake(gltf, bbmodel, output):
    scene = Scene(gltf)
    lengths = {a['name']: a['length'] for a in json.loads(Path(bbmodel).read_text())['animations']}
    uv = np.concatenate([a['TEXCOORD_0'] for a, *_ in scene.meshes])
    assert np.all((uv >= 0) & (uv <= 1))
    indices, offset = [], 0
    for a, ix, *_ in scene.meshes:
        indices.extend(ix + offset)
        offset += len(a['POSITION'])
    # Validate inverse binds against the exported bind-pose positions.
    source = np.concatenate([a['POSITION'] for a, *_ in scene.meshes])
    assert np.allclose(scene.frame()[:, :3], source, atol=2e-6)
    import io
    stream = io.BytesIO()
    def integer(n): stream.write(struct.pack('>i', n))
    def floats(a): stream.write(np.asarray(a, dtype='>f4').tobytes())
    stream.write(b'HSN1')
    integer(len(uv)); integer(len(indices)); floats(uv)
    stream.write(np.asarray(indices, dtype='>i4').tobytes())
    baked = {}
    for name in ['defalt', 'skill']:
        anim = next(a for a in scene.g['animations'] if a['name'] == name)
        duration = lengths[name]
        count = int(np.ceil(duration * 60)) + 1
        frames = np.array([scene.frame(anim, t) for t in np.linspace(0, duration, count)])
        floats([duration]); integer(count); floats(frames)
        baked[name] = frames
        print(name, 'duration', duration, 'frames', count, 'bounds', frames[:, :, :3].min(axis=(0, 1)), frames[:, :, :3].max(axis=(0, 1)))
    assert np.allclose(baked['defalt'][0], baked['defalt'][-1], atol=2e-5), 'Idle loop must close'
    for name, frames in baked.items():
        assert np.max(np.abs(frames[0] - frames[len(frames)//2])) > 0.01, name + ' must animate'
    Path(output).write_bytes(gzip.compress(stream.getvalue(), mtime=0))
    print('Vertices', len(uv), 'triangles', len(indices)//3, 'compressed bytes', Path(output).stat().st_size)
    return scene, baked, uv, np.array(indices)


if __name__ == '__main__':
    bake(*sys.argv[1:])
