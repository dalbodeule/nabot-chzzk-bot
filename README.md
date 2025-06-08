# chibot_chzzk_bot

[![Discord](https://img.shields.io/discord/1250093195870867577)](https://discord.gg/up8ANZegmy)&nbsp; &nbsp;[![Build Status](https://teamcity.mori.space/app/rest/builds/buildType:NabotChzzkBot_Build/statusIcon)](https://teamcity.mori.space/project/NabotChzzkBot)

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

### 관리 명령어 (on Chzzk chat)
- [x] !명령어추가 \[명령어] \[내용]
- [x] !명령어수정 \[명령어] \[내용]
- [x] !명령어삭제 \[명령어]
### 타이머 명령어 (on Chzzk chat, 매니저/스트리머 전용)
- [x] !시간 \[숫자: 분]
- [x] !시간 업타임
- [x] !시간 삭제
### 플레이리스트 명령어 (on Chzzk chat)
- [x] !노래추가 \[유튜브 주소]
- [x] !노래목록
- [ ] !노래삭제 \[번호]
- [ ] !노래설정 \[내용] \[켜기/끄기]


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
- [Nuxtjs](https://nuxt.com/)
- [Bulma](https://bulma.io/)