package space.mori.chzzk_bot.services

import space.mori.chzzk_bot.Database
import space.mori.chzzk_bot.discord.User

class UserService {
    fun saveUser(user: User) {
        val session = Database.sessionFactory.openSession()
        session.beginTransaction()
        session.persist(user)
        session.transaction.commit()
        session.close()
    }

    fun getUser(id: Long): User? {
        val session = Database.sessionFactory.openSession()
        val user = session.get(User::class.java, id)
        session.close()
        return user
    }
}