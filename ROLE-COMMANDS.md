# 역할 체험 명령어

OP 권한으로 다음 명령어를 사용합니다.

```mcfunction
/hunt role <플레이어> mangtae
/hunt role <플레이어> frankenstein
/hunt role <플레이어> medusa
/hunt role <플레이어> jack
/hunt role <플레이어> monster
/hunt role <플레이어> auto
```

- `mangtae`: 망태할아버지, `frankenstein`: 프랑켄슈타인, `medusa`: 메두사, `jack`: 잭.
- `monster`: 특수 능력 없는 몬스터. `auto`: 원래 닉네임 기준 배정으로 복구.
- `@s`, `@a` 등 플레이어 선택자도 지원합니다.
- 대기 중에는 장비와 크기가 적용됩니다. 스킬은 `/hunt start` 후 술래 대기 30초가 끝나면 사용합니다.
- 게임 중에는 생존한 몬스터의 역할만 변경할 수 있습니다. 술래와 탈락자의 역할은 종료 후 지정합니다.
- 지정은 다음 라운드에도 유지되며, `auto` 또는 서버 재시작으로 해제됩니다.
- 역할을 바꾸면 이전 역할의 장비·진행 중 변신·스킬 쿨타임을 정리하고 새 장비를 지급합니다.
- 여러 명에게 같은 역할을 줄 수 있습니다. 프랑켄슈타인의 시체와 부활 기회도 각자 관리합니다.

프랑켄슈타인 본체와 시체의 머리: `minecraft:carved_pumpkin`, CustomModelData 1002.
메두사 사망 시 떨어지는 머리: `minecraft:carved_pumpkin`, CustomModelData 1003.

동전은 손을 떠나는 시점을 0.45초에서 0.25초로 앞당겼습니다. 전체 동작/중복 방지는 3초입니다.
기본 방향은 가로로 180도 회전하며, 앞면과 뒷면 모두 위아래가 뒤집히지 않게 착지합니다.
