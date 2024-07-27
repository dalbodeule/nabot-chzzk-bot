# nabot_chzzk_bot

[![Discord](https://img.shields.io/discord/1250093195870867577)](https://discord.gg/up8ANZegmy)&nbsp; &nbsp;[![Build Status](https://teamcity.mori.space/app/rest/builds/buildType:NabotChzzkBot_Build/statusIcon)](https://teamcity.mori.space/project/NabotChzzkBot)&nbsp; &nbsp;[![Docker Image Version](https://img.shields.io/docker/v/dalbodeule/chzzkbot)](https://hub.docker.com/repository/docker/dalbodeule/chzzkbot/general)

## Chzzk Chatbot with [JDA5](https://github.com/discord-jda/JDA), [chzzk4j](https://github.com/R2turnTrue/chzzk4j)

네이버 게임스트리밍 플랫폼 치지직의 챗봇입니다.

## 지원 기능

### Placeholders

- [x] \<name>
- [x] \<following>
- [x] \<counter:counter_name>
- [x] \<personal_counter:counter_name>
- [x] \<daily_counter:counter_name>
- [x] \<days:yyyy-mm-dd>

### 관리 명령어 (on Discord)
- [x] /register chzzk_id: \[치지직 고유ID]
- [x] /alert channel: \[디스코드 Channel ID] content: \[알림 내용]
- [x] /add label: \[명령어] content: \[내용]
- [ ] /list
- [x] /update label: \[명령어] content: \[내용]
- [x] /delete label: \[명령어]
### 매니저 명령어 (on Discord)
- [x] /addmanager user: \[Discord user]
- [x] /listmanager
- [x] /removemanager user: \[Discord user]
### 관리 명령어 (on Chzzk chat)
- [x] !명령어추가 \[명령어] \[내용]
- [x] !명령어수정 \[명령어] \[내용]
- [x] !명령어삭제 \[명령어]

### Envs
- DISCORD_TOKEN
- DB_URL
- DB_USER
- DB_PASS
- RUN_AGENT = `false`
- NID_AUT
- NID_SES

### 사용 예시
- 팔로우
  - `/add label: !팔로우 content: <name>님은 오늘로 <following>일째 팔로우네요!`
- 출첵
  - `/add label: !출첵 content: <name>님의 <daily_counter:attendance>번째 출석! fail_content: <name>님은 오늘 이미 출석했어요! <daily_counter:attendance>번 했네요?`
  - `/add label: ? content: <name>님이 <counter:hook>개째 갈고리 수집`
- ㄱㅇㅇ
  - `/add label: ㄱㅇㅇ content: <counter:cute>번째 ㄱㅇㅇ`
  - `/add label: ㄱㅇㅇ content:  나누 귀여움 +<counter:cute>`
- 풉
  - `/add label: 풉 content: <counter:poop>번째 비웃음?`
  - `/add label: 풉키풉키 content: <counter:poop>번째 비웃음?`
- 바보
  - `/add label: 바보 content: 나 바보 아니다?`
  - `/add label: 바보 content: <counter:fool> 번째 바보? 나 바보 아니다?`
- 첫방송
  - `/add label: 첫방송 content: 24년 7월 23일부터 <days:2024-07-23>일 째 방송중!`

## 사용 기술스택
- [Exposed](https://github.com/JetBrains/Exposed)
- [Kotlin](https://github.com/JetBrains/kotlin)
- [JDA5](https://github.com/discord-jda/JDA)
- [chzzk4j](https://github.com/R2turnTrue/chzzk4j)
- [HikariCP](https://github.com/brettwooldridge/HikariCP)
- [gson](https://github.com/google/gson)
- [mariadb](https://mariadb.org/)
- [docker](https://www.docker.com/)
- [Teamcity](https://www.jetbrains.com/teamcity/)
