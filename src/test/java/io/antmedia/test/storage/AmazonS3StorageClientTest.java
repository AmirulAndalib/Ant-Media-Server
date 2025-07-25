package io.antmedia.test.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.antmedia.muxer.HLSMuxer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.amazonaws.AmazonClientException;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;

import io.antmedia.AppSettings;
import io.antmedia.storage.AmazonS3StorageClient;

public class AmazonS3StorageClientTest {

	public final String ACCESS_KEY = "";
	public final String SECRET_KEY = "";
	
	public final String BUCKET_NAME = "";
	
	public final String REGION = "";
	
	@Rule
	public TestRule watcher = new TestWatcher() {
		protected void starting(Description description) {
			System.out.println("Starting test: " + description.getMethodName());
		}

		protected void failed(Throwable e, Description description) {
			System.out.println("Failed test: " + description.getMethodName() );
			e.printStackTrace();
		}
		protected void finished(Description description) {
			System.out.println("Finishing test: " + description.getMethodName());
		}
	};
	
	@Test
	public void testListS3Objects() 
	{
		AmazonS3StorageClient storage = Mockito.spy(new AmazonS3StorageClient());
		List<String> objects = storage.getObjects("streams");
		assertEquals(0, objects.size());
		
		try {
			storage.setAccessKey(ACCESS_KEY);
			storage.setSecretKey(SECRET_KEY);
			storage.setRegion("eu-west-1");
			storage.setStorageName(BUCKET_NAME);
			storage.setEnabled(true);
			objects = storage.getObjects("streams");
			fail("it should throw exception above");
		}
		catch (Exception e) {
		}
		
		List<String> list = new ArrayList<>();
		List<S3ObjectSummary> objectSummaries = new ArrayList<>();
		objectSummaries.add(new S3ObjectSummary());
		storage.convert2List(objects, objectSummaries);
		
		assertEquals(1, objectSummaries.size());
		
	}
	
	@Test
	public void testS3() {
		AmazonS3StorageClient storage = Mockito.spy(new AmazonS3StorageClient());
		doReturn(null).when(storage).getObjects(anyString());

		storage.setAccessKey(ACCESS_KEY);
		storage.setSecretKey(SECRET_KEY);
		storage.setRegion("eu-west-1");
		storage.setStorageName(BUCKET_NAME);
		storage.setStorageClass("STANDARD");
		storage.setPathStyleAccessEnabled(false);
		
		File f = new File("src/test/resources/test.flv");
		storage.setEnabled(true);
		storage.save("streams" + "/" + f.getName() , f);
		
		Mockito.verify(storage).getTransferManager();
		Mockito.verify(storage).getObjects(anyString());

		storage.save("streams" + "/" + f.getName() , f);

		Mockito.verify(storage, Mockito.times(2)).getTransferManager();
	}
	
	@Test
	public void testException() {
		try {
			AmazonS3StorageClient storage = new AmazonS3StorageClient();

			storage.delete("streams/" + "any_file");
			
			storage.fileExist("any_file");
			
			storage.fileExist("streams/any_file");
						
			storage.save("streams/any_file", new File("any_file"));
			
			storage.setProgressListener(Mockito.mock(com.amazonaws.event.ProgressListener.class));
			storage.save("streams/any_file", new File("any_file"));
			
			storage.setMultipartUploadThreshold(5*1024*1024);
			
			storage.save("streams/any_file", new File("any_file"));
			
			assertEquals(5*1024*1024, storage.getMultipartUploadThreshold());
			storage.setRegion("us-east-1");
			
			TransferManager transferManager = storage.getTransferManager();
			assertEquals(transferManager, storage.getTransferManager());
			
			
			
		}
		catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	@Test
	public void testCheckStorageClass(){
		AmazonS3StorageClient storage = new AmazonS3StorageClient();
		assertTrue(storage.checkStorageClass("Glacier"));
		assertFalse(storage.checkStorageClass("WrongInput"));
		assertTrue(storage.checkStorageClass("standard"));
		assertTrue(storage.checkStorageClass("REDUCED_REDUNdancy"));
		assertTrue(storage.checkStorageClass("ONEZONE_IA"));
	}
	
	@Test
	public void testGetS3() {
		AmazonS3StorageClient storage = spy(new AmazonS3StorageClient());
		
		AmazonS3 amazonS3 = Mockito.mock(AmazonS3.class);
		Mockito.doReturn(amazonS3).when(storage).initAmazonS3();

		assertNull(storage.get("test"));
		
		
		S3Object s3Object = Mockito.mock(S3Object.class);
		when(amazonS3.getObject(Mockito.any(GetObjectRequest.class))).thenReturn(s3Object);
		
		when(s3Object.getObjectContent()).thenReturn(Mockito.mock(S3ObjectInputStream.class));
		
		assertNotNull(storage.get("test"));


		
	}
	
	@Test
	public void testDeleteLocalFile() {
		AmazonS3StorageClient storage = spy(new AmazonS3StorageClient());

		TransferManager tm = Mockito.mock(TransferManager.class);
		Mockito.doReturn(tm).when(storage).getTransferManager();
		Upload upload = Mockito.mock(Upload.class);
		Mockito.when(tm.upload(Mockito.any())).thenReturn(upload);
		storage.setEnabled(true);
		storage.setStorageClass("STANDARD");
		
		{
			ArgumentCaptor<ProgressListener> listener = ArgumentCaptor.forClass(ProgressListener.class);
			storage.save("key", new File("filename"), false);
			Mockito.verify(upload).addProgressListener(listener.capture());
			ProgressListener progressListener = listener.getValue();
			progressListener.progressChanged(new ProgressEvent(ProgressEventType.TRANSFER_COMPLETED_EVENT));
			Mockito.verify(storage, Mockito.never()).deleteFile(Mockito.any());
		}
		
		{
			ArgumentCaptor<ProgressListener> listener = ArgumentCaptor.forClass(ProgressListener.class);
			
			storage.save("key", new File("filename"), true);
			Mockito.verify(upload, Mockito.times(2)).addProgressListener(listener.capture());
			ProgressListener progressListener = listener.getValue();
			progressListener.progressChanged(new ProgressEvent(ProgressEventType.TRANSFER_COMPLETED_EVENT));
			Mockito.verify(storage, Mockito.times(1)).deleteFile(Mockito.any());
		}
		
		{
			ArgumentCaptor<ProgressListener> listener = ArgumentCaptor.forClass(ProgressListener.class);
			File file = new File("filename");
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			storage.save("key", file, true);
			Mockito.verify(upload, Mockito.times(3)).addProgressListener(listener.capture());
			ProgressListener progressListener = listener.getValue();
			progressListener.progressChanged(new ProgressEvent(ProgressEventType.TRANSFER_COMPLETED_EVENT));
			Mockito.verify(storage, Mockito.times(2)).deleteFile(Mockito.any());
			assertFalse(file.exists());
		}
		
		{
			File file = new File("filename");
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				storage.setCacheControl(null);
				storage.save("key", new FileInputStream(file), true);
				Mockito.verify(upload).waitForCompletion();
				assertTrue(file.exists());
			} catch (FileNotFoundException | AmazonClientException | InterruptedException e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
		
		
		
		
	}
	
	@Test
	public void testChangeS3Settings() 
	{
		AmazonS3StorageClient storage = spy(new AmazonS3StorageClient());
		Mockito.doReturn(Mockito.mock(AmazonS3.class)).when(storage).initAmazonS3();
		doReturn("").when(storage).getStorageName();


		//Call getAmazonS3 with default settings
		storage.getAmazonS3();
		Mockito.verify(storage, Mockito.times(1)).initAmazonS3();
		
		
		storage.getAmazonS3();
		//it should still be called 1 time
		Mockito.verify(storage, Mockito.times(1)).initAmazonS3();

		storage.reset();
		storage.getAmazonS3();
		//it should be called twice because it's reset
		Mockito.verify(storage, Mockito.times(2)).initAmazonS3();

	}
	
	@Test
	public void testCannedAcl() {
		AmazonS3StorageClient storage = new AmazonS3StorageClient();
		
		assertEquals(CannedAccessControlList.PublicRead, storage.getCannedAcl());
		
		storage.setPermission("nothing supported");
		
		assertEquals(CannedAccessControlList.PublicRead, storage.getCannedAcl());
		
		storage.setPermission("private");
		assertEquals(CannedAccessControlList.Private, storage.getCannedAcl());
		
		storage.setPermission("public-read-write");
		assertEquals(CannedAccessControlList.PublicReadWrite, storage.getCannedAcl());
		
		storage.setPermission("authenticated-read");
		assertEquals(CannedAccessControlList.AuthenticatedRead, storage.getCannedAcl());
		
		storage.setPermission("log-delivery-write");
		assertEquals(CannedAccessControlList.LogDeliveryWrite, storage.getCannedAcl());
		
		storage.setPermission("bucket-owner-read");
		assertEquals(CannedAccessControlList.BucketOwnerRead, storage.getCannedAcl());
		
		storage.setPermission("bucket-owner-full-control");
		assertEquals(CannedAccessControlList.BucketOwnerFullControl, storage.getCannedAcl());
		
		storage.setPermission("aws-exec-read");
		assertEquals(CannedAccessControlList.AwsExecRead, storage.getCannedAcl());
		
		
	}
	
	@Test
	public void testS3TransferBufferSize() {
		AmazonS3StorageClient storage = spy(new AmazonS3StorageClient());
		int s3TransferBufferSize = 5000;
		
		storage.setTransferBufferSize(s3TransferBufferSize);

		TransferManager tm = Mockito.mock(TransferManager.class);
		Mockito.doReturn(tm).when(storage).getTransferManager();
		Mockito.doReturn(true).when(storage).isEnabled();
		Mockito.doNothing().when(storage).listenUploadProgress(anyString(), nullable(File.class), anyBoolean(), any(), any());

		
		storage.save("key", null, mock(InputStream.class), false, false, null);

		ArgumentCaptor<PutObjectRequest> putObjectRequestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
		Mockito.verify(tm).upload(putObjectRequestCaptor.capture());
		assertEquals(s3TransferBufferSize, putObjectRequestCaptor.getValue().getRequestClientOptions().getReadLimit());

		
		
	}
	@Test
	public void testDeleteMultipleFile(){

		AmazonS3StorageClient s3client = Mockito.spy(AmazonS3StorageClient.class);


		ArrayList<String> objects = new ArrayList<>(Arrays.asList("test","test.mp4","test.abc","test.m3u8","test000000000.ts","test000000000.fmp4","testabc000000000.ts"));
		doReturn(objects).when(s3client).getObjects("test");


		s3client.deleteMultipleFiles(null,HLSMuxer.HLS_FILES_REGEX_MATCHER);
		verify(s3client,times(0)).getObjects(anyString());

		s3client.deleteMultipleFiles("test",HLSMuxer.HLS_FILES_REGEX_MATCHER);

		verify(s3client).delete("test000000000.ts");
		verify(s3client).delete("test000000000.fmp4");
		verify(s3client).delete("test.m3u8");

		verify(s3client,times(0)).delete("test");
		verify(s3client,times(0)).delete("test.mp4");
		verify(s3client,times(0)).delete("test.abc");
		verify(s3client,times(0)).delete("testabc000000000.ts");

	}
}
