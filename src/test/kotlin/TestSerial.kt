import com.chbachman.serial.Serial
import okio.Buffer
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class TestSerial {
    val buffer = Buffer()

    @Before fun resetBuffer() {
        buffer.clear()
    }

    inline fun <reified T: Any> assertReads(value: T) {
        Serial.write(buffer, value)
        val result = Serial.read<T>(buffer)
        assertEquals(value, result)
    }

    @Test fun testSerialTopLevel() {
        assertReads(100)
        assertReads("Hello, World")
        assertReads(55)
        assertReads(1.2)
        assertReads(1.3F)
        assertReads(1333L)
        assertReads(12213414)
    }

    @Test fun testSerialList() {
        data class SerialDataList(
            val foo: Int,
            val bar: List<Int>
        )

        assertReads(SerialDataList(12, listOf(123, 42, 41)))
        assertReads(SerialDataList(12, listOf(41)))
        assertReads(SerialDataList(12, listOf()))
        assertReads(SerialDataList(12, listOf(1, 1, 1, 1, 1)))
    }

    @Test fun testSerialPair() {
        data class SerialDataPair(
            val foo: String,
            val bar: Pair<Boolean, String>
        )

        assertReads(SerialDataPair("Hello", true to "Yes"))
        assertReads(SerialDataPair("Goodbye", false to "Bye"))
        assertReads(SerialDataPair("Foo", true to "Bar"))
        assertReads(SerialDataPair("Bar", false to "Red"))
    }
}

