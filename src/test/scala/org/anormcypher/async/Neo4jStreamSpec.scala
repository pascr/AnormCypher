package org.anormcypher.async

import org.anormcypher._
import org.scalatest._

import play.api.libs.iteratee._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.async.Async

class Neo4jStreamSpec extends FlatSpec with Matchers {
  val rand = scala.util.Random
  def nonZero(upTo: Int) = rand.nextInt(upTo) match {
    case 0 => 1
    case n => n
  }

  def randomSplit(s: String): Seq[String] = {
    def split1(acc: Vector[String], rem: String): Vector[String] = rem.length match {
      case n if n < 5 => acc :+ rem
      case n =>
        val (first, rest) = rem.splitAt(nonZero(n/2))
        split1(acc :+ first, rest)
    }
    split1(Vector[String](), s)
  }

  def chunking(whole: String): Enumerator[Array[Byte]] =
    (randomSplit(whole).map(s => Enumerator(s.getBytes)) :\ Enumerator.empty[Array[Byte]]) {
      (chunk, ret) => chunk andThen ret
    }

  "Neo4jStream" should "be able to adapt byte array stream to CypherResultRow" in {
    // TODO: use scalacheck to generate different types of neo4j rest responses
    val whole = """
    {"columns":["id","name"],"data":[[1,"Organism"],[2,"Gene Expression Role"],[3,"Mutation Type"],[4,"Gene"],[5,"DNA Part"],[6,"Plasmid"],[7,"Strain"],[8,"Mutation"],[9,"User"]]}
"""
    Async.async {
      val result = Async.await {
        Neo4jStream.parse(chunking(whole)) |>>> Iteratee.getChunks[CypherResultRow]
      }
      val metadata = result(0).metaData
      metadata shouldBe MetaData(List(
        MetaDataItem("id", false, "String"),
        MetaDataItem("name", false, "String")))

      result.map(_.data) shouldBe Seq(
        Seq(1, "Organism"),
        Seq(2, "Gene Expression Role"),
        Seq(3, "Mutation Type"),
        Seq(4, "Gene"),
        Seq(5, "DNA Part"),
        Seq(6, "Plasmid"),
        Seq(7, "Strain"),
        Seq(8, "Mutation"),
        Seq(9, "User")
      )
    }
  }
}