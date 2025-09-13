package nl.rvantwisk.server.udp.old

import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.output.CommandOutput
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer

@Serializable
data class Tile38Point(
    val type: String,
    val coordinates: List<Double>
)

@Serializable
data class Tile38Properties(
    val speed: Double,
    val src: String,
    val track: Double,
    val turn: Double,
    val vrate: Double
)

@Serializable
data class Tile38Object(
    val id: Long,
    val point: Tile38Point,
    val properties: Tile38Properties
)

class Tile38Output<K, V>(codec: RedisCodec<K, V>) : CommandOutput<K, V, Any>(codec, null) {

    data class C(var Counter:Int=0)

    private var depth=ArrayDeque<C>()

    private var currentId: Int? = null
    private var currentPoint: Tile38Point? = null
    private var currentProperties: Tile38Properties = Tile38Properties(0.0, "", 0.0, 0.0, 0.0)
    private var currentKey: String = ""
    override fun set(bytes: ByteBuffer) {
        val str = codec.decodeValue(bytes)
        output = str

        if (depth.size == 3 && depth.last().Counter==2) {
          //  currentId = codec.decodeValue(bytes).toInt()
        }
        if (depth.size == 3 && depth.last().Counter==1) {
        //    currentPoint = Json.decodeFromString(Tile38Point.serializer(),  codec.decodeValue(bytes))
        }
        if (depth.size == 4 && depth.last().Counter%2 == 0) {
            currentKey = codec.decodeValue(bytes).toString()
        }
        if (depth.size == 4 && depth.last().Counter%2 == 1) {
            println("They Key is: $currentKey ${currentKey.toString()}")
        }


        println("set(ByteBuffer): $str depth:${depth}")
        depth.last().Counter--
    }

    override fun set(value: Long) {
        output = value
        println("set(Long): $value depth:${depth}")
        depth.last().Counter--
    }

    override fun setError(error: ByteBuffer) {
        val str = codec.decodeValue(error)
        output = str
        println("setError: $str depth:${depth}")
    }

    fun set(value: String) {
        output = value
        println("set(String): $value  depth:${depth}")
        depth.last().Counter--
    }

    fun set(value: Any?) {
        output = value
        println("set(Any): $value  depth:${depth}")
        depth.last().Counter--
    }

    // Looks like the start of a new Tile38 packet
    override fun multi(count: Int) {
        depth.addLast(C(count))
        println("multi($count)  ${depth}")
    }

    override fun complete(depth: Int) {
        if (depth > 0 && depth < this.depth.size) {
            this.depth.removeLast()
        }
        println("complete($depth) $depth")
    }

    override fun multiPush(count: Int) {
        println("multiPush(Int): $count depth:${depth}")
    }
    override fun multiMap(count: Int) {
        println("multiMap(Int): $count depth:${depth}")
    }
    override fun multiSet(count: Int) {
        println("multiSet(Int): $count depth:${depth}")
    }
}