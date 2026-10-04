package com.wonderplay.download
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
class BoundedDownloadTest {
 @Test fun exactSizeTransfersWithProgress() {val output=ByteArrayOutputStream();var last=0L;assertEquals(5L,BoundedDownload.copy(ByteArrayInputStream(byteArrayOf(1,2,3,4,5)),output,5,progress={last=it}));assertEquals(5L,last);assertEquals(5,output.size())}
 @Test fun truncatedAndOversizedBodiesFail() {for(size in listOf(2,6)) assertThrows(IOException::class.java) {BoundedDownload.copy(ByteArrayInputStream(ByteArray(size)),ByteArrayOutputStream(),5)}}
 @Test fun cancelledTransferDoesNotContinue() {val output=ByteArrayOutputStream();assertThrows(kotlinx.coroutines.CancellationException::class.java) {BoundedDownload.copy(ByteArrayInputStream(ByteArray(100)),output,100,check={throw kotlinx.coroutines.CancellationException()})};assertEquals(0,output.size())}
}
