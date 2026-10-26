package com.example.citygo.skeleton.data.datastore

import java.security.MessageDigest

/** 稳定的本机账号分区。Token/昵称变化不改变分区，文件名不包含手机号。 */
object AccountStorage {
    fun keyFor(phone: String?): String = phone?.trim()?.takeIf { it.isNotEmpty() }?.let {
        MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    } ?: "guest"

    fun databaseName(key: String): String {
        require(key == "guest" || key.matches(Regex("[0-9a-f]{64}")))
        return "citygo_account_$key.db"
    }
}
