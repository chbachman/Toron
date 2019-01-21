import com.chbachman.toron.serial.createDB
import com.chbachman.toron.serial.mainDB
import com.chbachman.toron.serial.mainDBFile
import com.chbachman.toron.serial.repo
import com.chbachman.toron.util.insert
import mu.KotlinLogging
import okio.Buffer
import org.dizitart.no2.Nitrite
import org.dizitart.no2.objects.Id
import org.dizitart.no2.objects.ObjectRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import org.junit.rules.TestName
import java.util.*

class TestDB {
    companion object {
        private val tempFile = createTempFile()
        lateinit var testDB: Nitrite

        fun reloadDB(): Nitrite {
            tempFile.delete()
            mainDBFile.copyTo(tempFile)
            testDB = createDB(tempFile)
            return testDB
        }

        inline fun <reified T> repo(db: Nitrite = testDB): ObjectRepository<T> =
            db.getRepository(T::class.java)
    }
}

data class TestDataClass(
    @Id val x: String,
    val y: Int
)

fun randomTestData(): TestDataClass =
    TestDataClass(UUID.randomUUID().toString(), Random().nextInt())

class TestDBPerformance {
    @Rule @JvmField var name = TestName()
    private val logger = KotlinLogging.logger {  }
    private lateinit var repo: ObjectRepository<TestDataClass>
    private lateinit var data: List<TestDataClass>

    @Before fun setupDB() {
        TestDB.reloadDB()
        repo = TestDB.repo()
        data = List(100000) { randomTestData() }
    }

    private inline fun performance(closure: () -> Unit) {
        val time = measureTimeMillis(closure)
        logger.info { "${name.methodName} took ${time}ms" }
    }

    @Test fun testIndividual() = performance {
        repo.insert(data)
        assertEquals(repo.size(), 100000)
    }

    @Test fun testGroup() = performance {
        repo.insert(data.toTypedArray())
        assertEquals(repo.size(), 100000)
    }
}

