package com.eyeem.mjolnir;

import android.text.TextUtils;
import android.util.Log;

import com.android.volley.Request;
import com.squareup.mimecraft.Multipart;
import com.squareup.mimecraft.Part;



import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLContext;

import okhttp3.OkHttpClient;
import okhttp3.OkUrlFactory;

/**
 * Created by vishna on 22/11/13.
 */
public class SyncClient {

   protected RequestBuilder rb;
   protected ProgressCallback callback;
   protected Integer timeout;

   public SyncClient(RequestBuilder rb) {
      this.rb = rb;
   }

   public SyncClient timeout(int timeoutSeconds) {
      this.timeout = timeoutSeconds;
      return this;
   }

   public SyncClient callback(ProgressCallback callback) {
      this.callback = callback;
      return this;
   }

   public JSONObject json() throws Exception {
      return new JSONObject(raw());
   }

   public JSONObject jsonFromPath() throws Exception {
      return rb.declutter == null ? json() : rb.declutter.jsonObject(json());
   }

   public <E extends Object> E objectOf(Class clazz) throws Exception {
      return (E) ObjectRequest.fromJSON(clazz, rb.declutter == null ? json() : rb.declutter.jsonObject(json()));
   }

   public <E extends List> E  listOf(Class clazz) throws Exception {
      return (E) ListRequest.fromArray(clazz, rb.declutter.jsonArray(json()));
   }

   public String raw() throws Exception {
      HttpURLConnection connection = buildConnection();
      if (rb.method == Request.Method.PUT || rb.method == Request.Method.POST ||
         (rb.method == Request.Method.DELETE && !TextUtils.isEmpty(rb.content))) {

         if (!TextUtils.isEmpty(rb.content)) { // string content, e.g. json
            final byte[] bytes = rb.content.getBytes("UTF-8");
            connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
            connection.setRequestProperty("Content-Type", rb.content_type);
            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            try {
               os.write(bytes);
               os.flush();
            } finally {
               os.close();
            }
         } else if (rb.files.entrySet().size() == 0) { // url encoded
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            try {
               os.write(rb.toQuery().getBytes("UTF-8"));
               os.flush();
            } finally {
               os.close();
            }
         } else { // multipart
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(FilePart.MAX_BUFFER_SIZE);

            Multipart.Builder mb = new Multipart.Builder();
            mb.type(Multipart.Type.FORM);

            for (Map.Entry<String, RequestBuilder.StringWrapper> e : rb.params.entrySet()) {
               mb.addPart(
                  new Part.Builder()
                     .contentType("text/plain; charset=UTF-8")
                     .contentDisposition("form-data; name=\"" + e.getKey() + "\"")
                     .body(e.getValue().value)
                     .build()
               );
            }

            for (Map.Entry<String, String> e : rb.files.entrySet()) {
               File file = new File(e.getValue());
               if (!file.exists()) continue;
               mb.addPart(
                  new FilePart(file)
                     .callback(callback)
                     .contentType("application/octet-stream")
                     .contentDisposition("form-data; name=\"" + e.getKey() + "\"; filename=\"" + file.getName() + "\"")
               );
            }

            Multipart m = mb.build();

            for (Map.Entry<String, String> header : m.getHeaders().entrySet() ) {
               connection.setRequestProperty(header.getKey(), header.getValue());
            }

            OutputStream os = new BufferedOutputStream(connection.getOutputStream());
            try {
               m.writeBodyTo(os);
               os.flush();
            } finally {
               os.close();
            }
         }
      }
      return readConnection(connection);
   }

   protected HttpURLConnection buildConnection() throws IOException {
      URL url = new URL(rb.toUrl());
      OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
      clientBuilder.connectTimeout(timeout == null ? Constants.CONNECTION_TIMEOUT_IN_SEC : timeout, TimeUnit.SECONDS);
      clientBuilder.readTimeout(timeout == null ? Constants.CONNECTION_TIMEOUT_IN_SEC : timeout, TimeUnit.SECONDS);
      clientBuilder.writeTimeout(timeout == null ? Constants.CONNECTION_TIMEOUT_IN_SEC : timeout, TimeUnit.SECONDS);
      HttpURLConnection connection = new OkUrlFactory(clientBuilder.build()).open(url);

      connection.setRequestProperty("Accept-Encoding", "gzip");
      connection.setRequestMethod(rb.method());

      // headers
      for (Map.Entry<String, String> header : rb.headers.entrySet() ) {
         connection.setRequestProperty(header.getKey(), header.getValue());
      }

      connection.setUseCaches(false);

      return connection;
   }

   public String readConnection(HttpURLConnection connection) throws Exception {
      int code = 0;

      try {
         code = connection.getResponseCode();

         if (code >= 500 && code < 600)
            throw new Mjolnir(rb, code);
         if (Constants.DEBUG && code / 200 != 2)
            Log.i(Constants.TAG, String.format("%d : %s", code, rb.toUrl()));

         InputStream is = (code == 400 || code == 401 || code == 403) ? connection.getErrorStream() : connection.getInputStream();

         final String encoding = connection.getContentEncoding();
         if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
            is = new GZIPInputStream(is);
         }

         final String s = convertStreamToString(is);

         if (code < 200 || code >= 300) {
            throw new Mjolnir(rb, code, s);
         }

         if (Constants.DEBUG)
            Log.v(Constants.TAG, String.format("[OK] %d bytes read : %s", s.length(), rb.toUrl()));

         return s;
      } catch (Exception e) {
         throw e;
      } finally {
         connection.disconnect();
      }
   }

   private String convertStreamToString(InputStream is) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      StringBuilder sb = new StringBuilder();

      String line;
      try {
         while ((line = reader.readLine()) != null) {
            sb.append(line);
         }
      } catch (IOException e) {
         e.printStackTrace();
      } finally {
         try {
            is.close();
         } catch (IOException e) {
            if (Constants.DEBUG)
               Log.e(Constants.TAG, "SyncClient.convertToStream()", e);
         }
      }
      return sb.toString();
   }

   public static final class FilePart implements Part {
      public final static int MAX_BUFFER_SIZE = 8*1024;

      private final File file;
      private final byte[] buffer = new byte[MAX_BUFFER_SIZE];
      private final Map<String, String> headers;
      private ProgressCallback callback;

      @Override public Map<String, String> getHeaders() {
         return headers;
      }

      public FilePart(File file) {
         this.headers = new HashMap<String, String>();
         this.file = file;
      }

      public FilePart contentDisposition(String contentDisposition) {
         if (contentDisposition != null) {
            headers.put("Content-Disposition", contentDisposition);
         }
         return this;
      }

      public FilePart contentType(String contentType) {
         if (contentType != null) {
            headers.put("Content-Type", contentType);
         }
         return this;
      }

      public FilePart callback(ProgressCallback callback) {
         this.callback = callback;
         return this;
      }

      @Override public void writeBodyTo(OutputStream out) throws IOException {
         InputStream in = null;
         try {
            in = new FileInputStream(file);
            long totalBytes = file.length();
            long bytesUploaded = 0;
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
               out.write(buffer);
               bytesUploaded += bytesRead;
               if (callback != null) callback.transferred(file, bytesUploaded, totalBytes);
            }
         } finally {
            if (in != null) {
               try {
                  in.close();
               } catch (IOException ignored) {
               }
            }
         }
      }
   }

   public interface ProgressCallback {
      public void transferred(File file, long bytesUploaded, long totalBytes);
   }
}
