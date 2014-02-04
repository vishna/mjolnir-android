package com.eyeem.mjolnir;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HttpStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by vishna on 15/12/13.
 */
public class ObservableRequestQueue extends RequestQueue {

   /**
    * Default on-disk cache directory.
    */
   private static final String DEFAULT_CACHE_DIR = "volley";

   /** Number of network request dispatcher threads to start. */
   private static final int DEFAULT_NETWORK_THREAD_POOL_SIZE = 4;

   public ObservableRequestQueue(Cache cache, Network network, int threadPoolSize, ObservableResponseDelivery delivery) {
      super(cache, network, threadPoolSize, delivery);
   }

   public static ObservableRequestQueue newInstance(Context context, HttpStack stack) {
      File cacheDir = new File(context.getCacheDir(), DEFAULT_CACHE_DIR);

      if (stack == null) {
         stack = OkHttpStack.withSslWorkaround();
      }

      Network network = new BasicNetwork(stack);

      ObservableResponseDelivery ord = new ObservableResponseDelivery(new Handler(Looper.getMainLooper()));
      ObservableRequestQueue queue = new ObservableRequestQueue(new DiskBasedCache(cacheDir), network, DEFAULT_NETWORK_THREAD_POOL_SIZE, ord);
      ord.orq = queue;
      queue.start();

      return queue;
   }

   public static ObservableRequestQueue newInstance(Context context) {
      return newInstance(context, null);
   }

   @Override
   public Request add(Request request) {
      report(request, STATUS_ADDED, null);
      return super.add(request);
   }

   public final static int STATUS_ADDED = 0;
   public final static int STATUS_FAILED = 1;
   public final static int STATUS_SUCCESS = 2;
   public final static int STATUS_CANCELLED = 3;

   HashMap<String, Integer> statuses = new HashMap<String, Integer>();

   Set<Listener> listeners = new HashSet<Listener>();

   public void report(Request request, int status, Object data) {
      // TODO some sort of status id reporting
      for (Listener listener : listeners) {
         listener.onStatusUpdate(request, status, data);
      }
   }

   public void registerListener(Listener listener) {
      listeners.add(listener);
   }

   public void unregisterListener(Listener listener) {
      listeners.remove(listener);
   }

   public interface Listener {
      public void onStatusUpdate(Request request, int status, Object data);
   }
}