package nl.rvantwisk.server.udp.old

import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.output.CommandOutput
import java.nio.ByteBuffer

class Tile38OutputMsg<K, V>(codec: RedisCodec<K, V>) : CommandOutput<K, V, Any>(codec, null) {

    data class C(var Counter:Int=0)

    private var depth=ArrayDeque<C>()



    override fun set(bytes: ByteBuffer) {
        val str = codec.decodeValue(bytes)
        output = str
        println("Tile38Output.set(ByteBuffer): $str depth:$depth")
        depth.last().Counter--
    }

    override fun set(value: Long) {
        output = value
        println("Tile38Output.set(Long): $value depth:$depth")
        depth.last().Counter--
    }

    override fun setError(error: ByteBuffer) {
        val str = codec.decodeValue(error)
        output = str
        println("Tile38Output.setError: $str depth:$depth")
    }

    fun set(value: String) {
        output = value
        println("Tile38Output.set(String): $value  depth:$depth")
        depth.last().Counter--
    }

    fun set(value: Any?) {
        output = value
        println("Tile38Output.set(Any): $value  depth:$depth")
        depth.last().Counter--
    }

    // Looks like the start of a new Tile38 packet
    override fun multi(count: Int) {
        depth.addLast(C(count))
        println("Tile38Output.multi($count)  $depth")
        depth.last().Counter--
    }

    override fun complete(depth: Int) {
        if (depth > 0 && depth < this.depth.size) {
            this.depth.removeLast()
        }
        println("Tile38Output.complete($depth) $depth")
    }

    override fun multiPush(count: Int) {
        println("Tile38Output.multiPush(Int): $count depth:$depth")
    }
    override fun multiMap(count: Int) {
        println("Tile38Output.multiMap(Int): $count depth:$depth")
    }
    override fun multiSet(count: Int) {
        println("Tile38Output.multiSet(Int): $count depth:$depth")
    }
}