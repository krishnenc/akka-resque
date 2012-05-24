package org.akkaresque

object Base64 {
  val encodeTable = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

  def encode(fromBytes: Seq[Byte]) : String = {
    val encoded =
      group6Bits(fromBytes)
      .map(x => encodeChar(binaryToDecimal(x)))
      .mkString

    encoded + "=" * ((4 - encoded.length % 4) % 4) grouped(76) mkString "\n"
  }

  def encodeChar(i: Int) :Char = encodeTable(i)

  def binaryToDecimal(from: Seq[Int]): Int = {
    val len = from.length
    var sum = 0
    var i = 0
    while (i < len) {
      sum += from(len - i - 1) * math.pow(2, i).toInt
      i += 1
    }
    sum
  }

  def group6Bits(fromBytes: Seq[Byte]) :List[List[Int]] = {
    val BIT_LENGTH = 6
    val src = toBinarySeq(8)(fromBytes)
    trimList[Int](src.toList.grouped(BIT_LENGTH).toList, BIT_LENGTH, 0)
  }

  def toBinarySeq(bitLength: Int)(from: Seq[Byte]): Seq[Int] = {
    val result = scala.collection.mutable.Seq.fill(bitLength * from.length)(0)
    var i = 0
    while (i < bitLength * from.length) {
      result((i / bitLength) * bitLength + bitLength - (i % 8) - 1) = from(i / bitLength) >> (i % bitLength) & 1
      i += 1
    }
    result
  }

  def deleteEqual(src: String) :String = src.filter(_ != '=')

  def getEncodeTableIndexList(s: String): Seq[Int]= {
    deleteEqual(s).map(x => encodeTable.indexOf(x))
  }

  def decode(src: String) :Seq[Byte] = {
    val BIT_LENGTH = 8

    val indexSeq =
      getEncodeTableIndexList(src.filterNot(_ == '\n'))
      .map(x => toBinarySeq(6)(Seq.fill(1)(x.toByte)))

    deleteExtraZero(indexSeq.flatMap(s => s))
    .grouped(BIT_LENGTH)
    .map(binaryToDecimal(_).toByte).toSeq
  }

  def deleteExtraZero(s: Seq[Int]): Seq[Int] = {
    val BIT_LENGTH = 8
    s.take((s.length / BIT_LENGTH)  * BIT_LENGTH)
  }

  def trim[A](xs: List[A], n: Int, c: A): List[A] = {
    xs.length match {
      case l if l == n => xs
      case l if l < n  => xs ::: List.fill(n - l)(c)
      case l if l > n  => xs.take(n)
    }
  }

  def trimList[A](xss: List[List[A]], n: Int, c: A) :List[List[A]] = xss.map(xs => trim[A](xs, n, c))
}