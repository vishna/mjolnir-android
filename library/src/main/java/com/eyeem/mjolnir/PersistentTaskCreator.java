package com.eyeem.mjolnir;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;

/**
 * Created by vishna on 15/09/16.
 */

public class PersistentTaskCreator implements JobCreator {

   @Override public Job create(String className) {
      try {
         return (Job)Class.forName(className).newInstance();
      } catch (Throwable t) {
         return null;
      }
   }
}
