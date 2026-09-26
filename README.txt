Pitwall F1 — 안드로이드 앱(APK) + 홈 화면 위젯

앱을 열면 우리가 만든 대시보드(https://cooper0-car.github.io/PitWallF1/)가 뜨고,
위젯에는 다음 레이스 · 결승까지 카운트다운 · 다음 세션이 나온다. 위젯을 누르면 앱이 열린다.
APK는 깃허브가 자동으로 만들어준다 (PC에 설치할 것 없음).

[1] 깃허브에 올리기 — PC에서
 1. github.com → 오른쪽 위 [+] → New repository
    이름: PitWallF1-android / Public 선택 / Create repository
 2. "uploading an existing file" 클릭
 3. 이 폴더(PitWallF1-android) "안의" 모든 것을 한꺼번에 드래그
      .github 폴더, app 폴더, build.gradle, settings.gradle,
      gradle.properties, pitwall.keystore, .gitignore ...
    → 아래 Commit changes
 4. 위쪽 [Actions] 탭 → "Build APK" 가 돌아감 (3~6분)
    초록 체크가 뜨면 완료

  ※ Actions 탭에 아무것도 없으면 .github 폴더가 안 올라간 것:
    Add file → Create new file → 파일 이름 칸에  .github/workflows/build.yml  입력
    → build_yml_copy.txt 내용을 전부 복사해서 붙여넣기 → Commit

[2] 폰에 설치
 1. 폰 크롬에서 열기:
      https://github.com/cooper0-car/PitWallF1-android/releases/latest
 2. PitwallF1.apk 눌러서 다운로드 → 열기
 3. "출처를 알 수 없는 앱" 허용 → 설치
    (Play 프로텍트 경고가 뜨면 "무시하고 설치" / "세부정보 → 설치")

[3] 위젯 올리기
 1. 홈 화면 빈 곳 길게 누르기 → 위젯
 2. Pitwall F1 찾아서 길게 눌러 맨 위에 놓기
 3. 크기는 가장자리를 끌어서 조절 (기본 4×2)

[업데이트]
 - 화면(대시보드)만 고칠 때: PitWallF1 저장소에 index.html 만 다시 올리면 앱에도 바로 반영
 - 앱/위젯 코드를 고칠 때: 이 저장소에 파일 올리기 → 자동 빌드 → [2]처럼 다시 설치 (덮어쓰기)

[참고]
 - pitwall.keystore 는 앱 서명 파일. 항상 같은 서명이라 새 버전을 덮어쓰기 설치할 수 있음.
   개인용이라 공개 저장소에 둬도 되지만, 이 앱을 남에게 배포할 계획이면 따로 보관하는 게 좋음.
 - 위젯 카운트다운: 결승 48시간 전부터 초 단위로 실시간 표시, 그 전에는 "3일 5시간"처럼 표시.
 - 시간은 폰 시간대 기준으로 표시.
