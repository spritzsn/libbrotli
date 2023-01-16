package io.github.edadma.brotli

import io.github.edadma.brotli.extern.LibBrotli as lib

import scala.collection.immutable.ArraySeq
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

enum EncoderMode:
  case GENERIC, TEXT, FONT

enum DecoderResult:
  case ERROR, SUCCESS, NEEDS_MORE_INPUT, NEEDS_MORE_OUTPUT

val DEFAULT_QUALITY = 11

val DEFAULT_WINDOW = 22

private def copy(seq: IndexedSeq[Byte])(implicit zone: Zone): Ptr[Byte] =
  val buf = alloc[Byte](seq.length.toUInt)
  var i = 0

  while i < seq.length do
    buf(i) = seq(i)
    i += 1

  buf

private def copy(buf: Ptr[Byte], size: Int): IndexedSeq[Byte] =
  val arr = new Array[Byte](size)
  var i = 0

  while i < size do
    arr(i) = buf(i)
    i += 1

  arr to ArraySeq

def encoderCompress(quality: Int, lgwin: Int, mode: EncoderMode, input: IndexedSeq[Byte]): Option[IndexedSeq[Byte]] =
  Zone { implicit z =>
    val size = (input.length + 1000).toUInt
    val encoded_buffer = alloc[Byte](size)
    val encoded_size = stackalloc[CSize]()

    !encoded_size = size

    if lib.BrotliEncoderCompress(
        quality,
        lgwin,
        mode.ordinal,
        input.length.toUInt,
        copy(input),
        encoded_size,
        encoded_buffer,
      ) == 0
    then None
    else Some(copy(encoded_buffer, (!encoded_size).toInt))
  }
