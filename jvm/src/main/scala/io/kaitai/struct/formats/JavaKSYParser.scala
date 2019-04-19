package io.kaitai.struct.formats

import java.io._
import java.nio.charset.Charset
import java.util.{List => JList, Map => JMap}

import io.kaitai.struct.JavaMain.CLIConfig
import io.kaitai.struct.format.{ClassSpec, ClassSpecs}
import io.kaitai.struct.{Log, Main}
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object JavaKSYParser {
  def localFileToSpecs(yamlFilename: String, config: CLIConfig): ClassSpecs = {
    val firstSpec = fileNameToSpec(yamlFilename)
    val yamlDir = Option(new File(yamlFilename).getParent).getOrElse(".")
    val specs = new JavaClassSpecs(yamlDir, config.importPaths, firstSpec)

    Await.result(Main.importAndPrecompile(specs, config.runtime), Duration.Inf)
    specs
  }

  def fileNameToSpec(yamlFilename: String): ClassSpec = {
    Log.fileOps.info(() => s"reading $yamlFilename...")

    // This complex string of classes is due to the fact that Java's
    // default "FileReader" implementation always uses system locale,
    // which screws up encoding on some systems and screws up reading
    // UTF-8 files with BOM
    val fis = new FileInputStream(yamlFilename)
    val isr = new InputStreamReader(fis, Charset.forName("UTF-8"))
    val br = new BufferedReader(isr)
    val scalaSrc = readerToYaml(br)
    ClassSpec.fromYaml(scalaSrc)
  }

  def readerToYaml(reader: Reader): Any = {
    val yamlLoader = new Yaml(new SafeConstructor)
    val javaSrc = yamlLoader.load(reader)
    yamlJavaToScala(javaSrc)
  }

  def stringToYaml(data: String): Any = {
    val yamlLoader = new Yaml(new SafeConstructor)
    val javaSrc = yamlLoader.load(data)
    yamlJavaToScala(javaSrc)
  }

  def yamlJavaToScala(src: Any): Any = {
    src match {
      case jlist: JList[AnyRef] =>
        jlist.toList.map(yamlJavaToScala)
      case jmap: JMap[String, AnyRef] =>
        jmap.toMap.mapValues(yamlJavaToScala)
      case _: String =>
        src
      case _: Double =>
        src
      case _: Boolean =>
        src
      case javaInt: java.lang.Integer =>
        javaInt.intValue
      case javaLong: java.lang.Long =>
        javaLong.longValue
      case _: java.math.BigInteger =>
        src.toString
      case null =>
        // may be not the very best idea, but these nulls
        // should be handled by real parsing code, i.e. where
        // it tracks tree depth, etc.
        null
    }
  }
}
