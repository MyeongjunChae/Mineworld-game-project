# Mineworld game project

Java 21과 LWJGL 3로 만든 1인칭 복셀 샌드박스 프로토타입입니다. 지형과 나무가 생성되는 월드에서 블록을 캐고 설치할 수 있으며, 월드는 사용자 홈 폴더에 저장할 수 있습니다.

## 실행

Windows에서 Java 21을 설치한 뒤 저장소 루트에서 실행합니다.

```powershell
.\gradlew.bat run
```

## 조작

| 입력 | 동작 |
| --- | --- |
| W/A/S/D | 이동 |
| Space | 점프 |
| Left Shift | 달리기 |
| 마우스 이동 | 시점 회전 |
| 왼쪽 클릭 | 바라보는 블록 캐기 |
| 오른쪽 클릭 | 선택한 블록 설치 |
| 1–9 | 핫바 선택 |
| Esc | 마우스 커서 해제 |
| F3 | 마우스 다시 잠금 |
| F5 | 월드 저장 |
| F9 | 저장된 월드 불러오기 |

월드 저장 파일은 `%USERPROFILE%\.mineworld-game-project\world.dat`에 생성됩니다.

## 테스트

```powershell
.\gradlew.bat test
```
