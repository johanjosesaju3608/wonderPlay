package com.wonderplay.download
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
internal object BoundedDownload {
 fun copy(input:InputStream,output:OutputStream,expected:Long,maximum:Long=PermittedAudioSource.MAX_BYTES,check:()->Unit={},progress:(Long)->Unit={}):Long {
  if(expected !in 1..maximum) throw IOException("This download is too large.")
  val buffer=ByteArray(32768);var bytes=0L
  while(true) {
   check();val count=input.read(buffer);if(count<0) break
   bytes+=count
   if(bytes>expected || bytes>maximum) throw IOException("The source file size changed. Try again.")
   output.write(buffer,0,count);progress(bytes)
  }
  output.flush()
  if(bytes!=expected) throw IOException("The download was incomplete. Try again.")
  return bytes
 }
}
