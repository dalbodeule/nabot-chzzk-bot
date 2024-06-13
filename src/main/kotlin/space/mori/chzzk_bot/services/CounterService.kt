package space.mori.chzzk_bot.services

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import space.mori.chzzk_bot.models.*
import java.time.LocalDate

object CounterService {
    fun getCounterValue(name: String, user: User): Int {
        return transaction {
            Counter.find {
                (Counters.name eq name) and (Counters.user eq user.id)
            }.singleOrNull()?.value ?: 0
        }
    }

    fun updateCounterValue(name: String, increment: Int, user: User): Int {
        return transaction {
            val counter = Counter.find {
                (Counters.name eq name) and (Counters.user eq user.id) }.singleOrNull()
            return@transaction if (counter != null) {
                counter.value += increment
                counter.value
            } else {
                val newCounter = Counter.new {
                    this.name = name
                    this.value = increment
                    this.user = user
                }
                newCounter.value
            }
        }
    }

    fun getPersonalCounterValue(name: String, userId: String, user: User): Int {
        return transaction {
            PersonalCounter.find {
                (PersonalCounters.name eq name) and (PersonalCounters.userId eq userId) and (PersonalCounters.user eq user.id)
            }.singleOrNull()?.value ?: 0
        }
    }

    fun updatePersonalCounterValue(name: String, userId: String, increment: Int, user: User): Int {
        return transaction {
            val counter = PersonalCounter.find {
                (PersonalCounters.name eq name) and (PersonalCounters.userId eq userId) and (PersonalCounters.user eq user.id)
            }.singleOrNull()

            return@transaction if (counter != null) {
                counter.value += increment
                counter.value
            } else {
                val newCounter = PersonalCounter.new {
                    this.name = name
                    this.value = increment
                    this.userId = userId
                    this.user = user
                }
                newCounter.value
            }
        }
    }

    fun getDailyCounterValue(name: String, userId: String, user: User): Pair<Int, Boolean> {
        val today = LocalDate.now()

        return transaction {
            val counter = DailyCounter.find {
                (DailyCounters.name eq name) and (DailyCounters.userId eq userId) and (DailyCounters.user eq user.id)
            }.singleOrNull()

            Pair(counter?.value ?: 0, counter?.updatedAt != today)
        }
    }

    fun updateDailyCounterValue(name: String, userId: String, increment: Int, user: User): Pair<Int, Boolean> {
        val today = LocalDate.now()

        return transaction {
            val counter = DailyCounter.find {
                (DailyCounters.name eq name) and (DailyCounters.userId eq userId) and (DailyCounters.user eq user.id)
            }.singleOrNull()

            println("$counter")

            if(counter == null) {
                val newCounter = DailyCounter.new {
                    this.name = name
                    this.value = increment
                    this.userId = userId
                    this.updatedAt = today
                    this.user = user
                }
                return@transaction Pair(newCounter.value, true)
            }

            return@transaction if(counter.updatedAt == today)
                Pair(counter.value, false)
            else {
                counter.value += increment
                Pair(counter.value, true)
            }
        }
    }
}