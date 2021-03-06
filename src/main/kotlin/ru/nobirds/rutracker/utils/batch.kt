package ru.nobirds.rutracker.utils

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter
import java.io.Closeable
import java.util.ArrayList
import java.util.LinkedHashMap
import kotlin.collections.contains
import kotlin.collections.isNotEmpty

interface Batcher<T> : Closeable {

    fun add(value:T)

}

abstract class AbstractBatcher<T>(val batchSize:Long) : Batcher<T> {

    private val batch = ArrayList<T>()

    override fun add(value: T) {
        batch.add(value)

        if (batch.size == batchSize.toInt()) {
            flush()
        }
    }

    private fun flush() {
        if (batch.isNotEmpty()) {
            flushImpl(batch)
            batch.clear()
        }
    }

    override fun close() {
        flush()
    }

    abstract fun flushImpl(batch: List<T>)

}

class SimpleJdbcBatcher<T>(batchSize:Long, val sql: String,
                           val jdbcTemplate: JdbcTemplate,
                           val setter:ParameterizedPreparedStatementSetter<T>) : AbstractBatcher<T>(batchSize) {

    override fun flushImpl(batch: List<T>) {
        jdbcTemplate.batchUpdate(sql, batch, batch.size, setter)
    }

}

class UniqueKeyJdbcBatcher<K, T>(val batchSize:Long,
                                 val sql: String,
                                 val jdbcTemplate: JdbcTemplate,
                                 val setter:ParameterizedPreparedStatementSetter<T>,
                                 val fetcher: (T)->K,
                                 val checker:(K)->Boolean) : Batcher<T> {

    private val batch = LinkedHashMap<K, T>()

    override fun add(value: T) {
        val key = fetcher(value)

        if (key !in batch && !checker(key)) {
            batch.put(key, value)
            if (batch.size == batchSize.toInt()) {
                flush()
            }
        }
    }

    private fun flush() {
        if (batch.isNotEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch.values, batch.size, setter)
            batch.clear()
        }
    }

    override fun close() {
        flush()
    }

}

