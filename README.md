# Aerospike Spark Connector
The Aerospike spark connector provides features to represent data stored in Aerospike as a DataFrame in Spark.
 
Aerospike Spark Connector includes:
- Reading from Aerospike to a DataFrame
- Saving a DataFrame to Aerospike
- Spark SQL multiple filters pushed down to the Aerospike cluster

## How to build

The source code for this solution is available on GitHub at [https://github.com/aerospike/aerospark](https://github.com/aerospike/aerospark). SBT is the build tool and it will create a Uber (fat) jar as the final output of the build process. The jar will contain all the class files and dependencies.

This Library requires Java JDK 7+ Scala 2.10, SBT 0.13, Maven and the `aerospike-helper-java` 

Before you build the Aerospike spark connector you need to build the `aerospike-helper-java` JAR and install it in your local maven repository. The `aerospike-helper-java` JAR is used by the connector to perform efficent, multi-filter queries on Aerospike.

Clone the [Aerospike Helper](https://github.com/aerospike/aerospike-helper) repository using this command:
```bash
$ git clone https://github.com/aerospike/aerospike-helper
```
Navigate to the subdirectory `java` and run the following command to build and install the helper class jar:
```bash
$ mvn clean install -DskipTests
```
When maven is complete the `aerospike-helper-java` JAR will be installed in your local maven repository

To build the Spark connector:
Clone the [Aerospike Spark](https://github.com/citrusleaf/aerospark/.git) repository using this command:
```bash
$ git clone https://github.com/citrusleaf/aerospark/.git
```
After cloning the repository, build the uber jar using:
```bash
$ sbt assembly
```
Note that during the build, a number of unit tests are run, these tests will assume an Aerospike cluster is running at "127.0.0.1" on port 3000. If you want to ignore the unit tests, use:
```bash
$ sbt 'set test in assembly := {}' clean assembly
```

On conclusion of the build, the uber JAR `aerospike-spark-assembly-<version>.jar` will be located in the subdirectory `target/scala-2.10`.

## Usage
The assembled JAR can be used in any Spark application providing it's on the class path.
### spark shell
To use connector with the spark-shell, use the `--jars` command line option and include the path to the assembled JAR.
Example:
```bash
$ spark-shell --master local[*] --jars target/scala-2.10/aerospike-spark-assembly-1.1.0.jar
```
Import the `com.aerospike.spark.sql._` package
```scala
scala> import com.aerospike.spark.sql._
import com.aerospike.spark.sql._
```
and any Aerospike packages and classes. For example:
```scala
scala> import com.aerospike.client.AerospikeClient
import com.aerospike.client.AerospikeClient

scala> import com.aerospike.client.Bin
import com.aerospike.client.Bin

scala> import com.aerospike.client.Key
import com.aerospike.client.Key

scala> import com.aerospike.client.Value
import com.aerospike.client.Value

```
Load some data into Aerospike with:
```scala
    val TEST_COUNT = 100
    val namespace = "test"
    var client = AerospikeConnection.getClient("localhost", 3000)
    Value.UseDoubleType = true
    for (i <- 1 to TEST_COUNT) {
      val key = new Key(namespace, "rdd-test", "rdd-test-"+i)
      client.put(null, key,
         new Bin("one", i),
         new Bin("two", "two:"+i),
         new Bin("three", i.toDouble)
      )
    }

```
Try a test with the loaded data:
```scala
	val thingsDF = sqlContext.read.
			format("com.aerospike.spark.sql").
			option("aerospike.seedhost", "127.0.0.1").
			option("aerospike.port", "3000").
			option("aerospike.namespace", namespace).
			option("aerospike.set", "rdd-test").
			load 
	thingsDF.registerTempTable("things")
	val filteredThings = sqlContext.sql("select * from things where one = 55")
	val thing = filteredThings.first()
```

### Loading and Saving DataFrames 
The Aerospike Spark connector provides functions to load data from Aerospike into a DataFrame and save a DataFrame into Aerospike

#### Loading data

```scala
	val thingsDF = sqlContext.read.
		format("com.aerospike.spark.sql").
		option("aerospike.seedhost", "127.0.0.1").
		option("aerospike.port", "3000").
		option("aerospike.namespace", "test").
		option("aerospike.set", "rdd-test").
		load 
```

You can see that the read function is configured by a number of options, these are:
- `format("com.aerospike.spark.sql")` specifies the function library to load the DataFrame.
- `option("aerospike.seedhost", "127.0.0.1")` specifies a seed host in the Aerospike cluster.
- `option("aerospike.port", "3000")` specifies the port to be used
- `option("aerospike.namespace", "test")` specifies the Namespace name to be used e.g. "test"
- `option("aerospike.set", "rdd-test")` specifies the Set to be used e.g. "rdd-test"
Spark SQL can be used to efficently filter (where lastName = 'Smith') Bin values represented as columns. The filter is passed down to the Aerospike cluster and filtering is done in the server. Here is an example using filtering:
```scala
	val thingsDF = sqlContext.read.
		format("com.aerospike.spark.sql").
		option("aerospike.seedhost", "127.0.0.1").
		option("aerospike.port", "3000").
		option("aerospike.namespace", namespace).
		option("aerospike.set", "rdd-test").
		load 
	thingsDF.registerTempTable("things")
	val filteredThings = sqlContext.sql("select * from things where one = 55")

```

Additional meta-data columns are automatically included when reading from Aerospike, the default names are:
- `__key` the values of the primary key if it is stored in Aerospike
- `__digest` the digest as Array[byte]
- `__generation` the gereration value of the record read
- `__expitation` the expiration epoch
- `__ttl` the time to live value calcualed from the expiration - now
 
These meta-data column name defaults can be be changed by using additional options during read or write, for example:
```scala
	val thingsDF = sqlContext.read.
		format("com.aerospike.spark.sql").
		option("aerospike.seedhost", "127.0.0.1").
		option("aerospike.port", "3000").
		option("aerospike.namespace", "test").
		option("aerospike.set", "rdd-test").
		option("aerospike.expiryColumn", "_my_expiry_column").
		load 
```

#### Saving data
A DataFrame can be saved in Aerospike by specifying a column in the DataFrame as the Primary Key or the Digest.
##### Saving by Digest
In this example, the value of the digest is specified by the "__digest" column in the DataFrame.
```scala
	val thingsDF = sqlContext.read.
		format("com.aerospike.spark.sql").
		option("aerospike.seedhost", "127.0.0.1").
		option("aerospike.port", "3000").
		option("aerospike.namespace", namespace).
		option("aerospike.set", "rdd-test").
		load 
		
    thingsDF.write.
        mode(SaveMode.Overwrite).
        format("com.aerospike.spark.sql").
        option("aerospike.seedhost", "127.0.0.1").
		option("aerospike.port", "3000").
		option("aerospike.namespace", namespace).
		option("aerospike.set", "rdd-test").
		option("aerospike.updateByDigest", "__digest").
        save()                

```
##### Saving by Key
In this example, the value of the primary key is specified by the "key" column in the DataFrame.
```scala
      import org.apache.spark.sql.types.StructType
      import org.apache.spark.sql.types.StructField
      import org.apache.spark.sql.types.LongType
      import org.apache.spark.sql.types.StringType
      import org.apache.spark.sql.DataFrame
      import org.apache.spark.sql.Row
      import org.apache.spark.sql.SaveMode


      val namespace = "test"
      val setName = "new-rdd-data"
      
      val schema = new StructType(Array(
          StructField("key",StringType,nullable = false),
          StructField("last",StringType,nullable = true),
          StructField("first",StringType,nullable = true),
          StructField("when",LongType,nullable = true)
          )) 
      val rows = Seq(
          Row("Fraser_Malcolm","Fraser", "Malcolm", 1975L),
          Row("Hawke_Bob","Hawke", "Bob", 1983L),
          Row("Keating_Paul","Keating", "Paul", 1991L), 
          Row("Howard_John","Howard", "John", 1996L), 
          Row("Rudd_Kevin","Rudd", "Kevin", 2007L), 
          Row("Gillard_Julia","Gillard", "Julia", 2010L), 
          Row("Abbott_Tony","Abbott", "Tony", 2013L), 
          Row("Tunrbull_Malcom","Tunrbull", "Malcom", 2015L)
          )
          
      val inputRDD = sc.parallelize(rows)
      
      val newDF = sqlContext.createDataFrame(inputRDD, schema)
  
      newDF.write.
        mode(SaveMode.Ignore).
        format("com.aerospike.spark.sql").
        option("aerospike.seedhost", "127.0.0.1").
						option("aerospike.port", "3000").
						option("aerospike.namespace", namespace).
						option("aerospike.set", setName).
						option("aerospike.updateByKey", "key").
        save()       
```
##### Using TTL while saving 
Time to live (TTL) can be set individually on each record. The TTL should be stored in a column in the DataSet before it is saved. 

To enable updates to TTL, and additional option is specified:
```scala
	option("aerospike.ttlColumn", "expiry")
```
### Schema
Aerospike is Schema-less and Spark DataFrames use a Schema. To facilitate the need for schema, the Aerospike spark connector samples 100 records, via a scan, and reads the Bin names and infers the Bin type.

The number of records scanned can be changed by using the option:

```scala
	option("aerospike.schema.scan", 20)
```
Note: the schema is derived each time `load` is called. If you call `load` before the Aerospike namespace/set has any data, only the meta-data columns will be available.

