package io.github.spritzsn.libbrotli

import io.github.spritzsn.libbrotli.extern.LibBrotli as lib

import scala.collection.immutable.ArraySeq
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

enum EncoderMode:
  case GENERIC, TEXT, FONT

enum EncoderParameter:
  case MODE, QUALITY, LGWIN, LGBLOCK, DISABLE_LITERAL_CONTEXT_MODELING, SIZE_HINT, LARGE_WINDOW, NPOSTFIX, NDIRECT,
    STREAM_OFFSET

//enum DecoderResult:
//  case ERROR, SUCCESS, NEEDS_MORE_INPUT, NEEDS_MORE_OUTPUT

val DEFAULT_QUALITY = 11

val DEFAULT_WINDOW = 22

private val ERROR = 0

private def copy(seq: collection.IndexedSeq[Byte])(implicit zone: Zone): Ptr[Byte] =
  val buf = alloc[Byte](seq.length.toUInt)
  var i = 0

  while i < seq.length do
    buf(i) = seq(i)
    i += 1

  buf

private def copy(buf: Ptr[Byte], size: Int): Array[Byte] =
  val arr = new Array[Byte](size)
  var i = 0

  while i < size do
    arr(i) = buf(i)
    i += 1

  arr

def encoderVersion: Int = lib.BrotliEncoderVersion
def encoderCompress(
    quality: Int,
    lgwin: Int,
    mode: EncoderMode,
    input: collection.IndexedSeq[Byte],
): Option[Array[Byte]] =
  Zone { implicit z =>
    val size = lib.BrotliEncoderMaxCompressedSize(input.length.toULong)
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
      ) == ERROR
    then None
    else Some(copy(encoded_buffer, (!encoded_size).toInt))
  }

enum EncoderOperation:
  case PROCESS, FLUSH, FINISH, EMIT_METADATA

implicit class EncoderState(val stateptr: lib.encoderState_tp):
  def destroyInstance(): Unit = lib.BrotliEncoderDestroyInstance(stateptr)
  def hasMoreOutput: Boolean = lib.BrotliEncoderHasMoreOutput(stateptr) != 0
  def isFinished: Boolean = lib.BrotliEncoderIsFinished(stateptr) != 0
  def setParameter(param: EncoderParameter, value: Int): Boolean =
    lib.BrotliEncoderSetParameter(stateptr, param.ordinal, value) != 0
  def takeOutput(max: Int): Array[Byte] =
    val size = stackalloc[CSize]()

    !size = max.toUInt
    copy(lib.BrotliEncoderTakeOutput(stateptr, size), (!size).toInt)
  def encoderCompressStream(input: collection.IndexedSeq[Byte], op: EncoderOperation): Option[(Int, Array[Byte])] =
    Zone { implicit z =>
      val available_in = stackalloc[CSize]()

      !available_in = input.length.toUInt

      val next_in = stackalloc[Ptr[Byte]]()

      !next_in = copy(input)

      val available_out = stackalloc[CSize]()
      val size = lib.BrotliEncoderMaxCompressedSize(input.length.toULong)

      !available_out = size

      val next_out = stackalloc[Ptr[Byte]]()
      val encoded_buffer = alloc[Byte](size)

      !next_out = encoded_buffer

      if lib.BrotliEncoderCompressStream(
          stateptr,
          op.ordinal,
          available_in,
          next_in,
          available_out,
          next_out,
          null,
        ) == 0
      then None
      else Some(((!available_in).toInt, copy(encoded_buffer, size.toInt - (!available_out).toInt)))
    }

def encoderCreateInstance: EncoderState = lib.BrotliEncoderCreateInstance(null, null, null)
def encoderMaxCompressedSize(input_size: Long): Long = lib.BrotliEncoderMaxCompressedSize(input_size.toULong).toLong

def decoderDecompress(encoded: collection.IndexedSeq[Byte]): Option[Array[Byte]] = Zone { implicit z =>
  val size = (encoded.length * 7).toUInt
  val decoded_buffer = alloc[Byte](size)
  val decoded_size = stackalloc[CSize]()

  !decoded_size = size

  if lib.BrotliDecoderDecompress(encoded.length.toUInt, copy(encoded), decoded_size, decoded_buffer) == ERROR then None
  else Some(copy(decoded_buffer, (!decoded_size).toInt))
}
