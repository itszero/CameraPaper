#include <stdlib.h>
#include <jni.h>
#include <android/log.h>

#define LOG_TAG "CameraPaperNativeLib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// RGB amount ( 30 for each )
#define RGB_DARK_THRESHOLD 30

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
  return JNI_VERSION_1_4;
}

jintArray
Java_idv_Zero_CameraPaper_CameraPaperService_00024CameraPaperEngine_nativeDecodeYUVAndRotate( JNIEnv* env,
                                                  jobject thiz, jobject jfg, jint width, jint height )
{
  int size = width * height;
  int hw = width, hh = height;
  int ns = hw * hh;
  
  jbyte *fg = ((jbyte*)(*env)->GetDirectBufferAddress(env, jfg));
  jint *out = (jint*)malloc((ns + 1) * sizeof(jint));

  int i, j;
  int Y, Cr=0, Cb=0;
  int darkRegion = 0;
  
  for(j=1;j<height;j++)
  {
    int pixPtr = j * width;
    int jDiv2 = j >> 1;

    for (i=0;i<width;i++)
    {
      Y = fg[pixPtr];
      if (Y<0) Y += 255;
      if ((i & 0x1) != 1)
      {
        int cOff = size + jDiv2 * width + (i >> 1) * 2;
        Cb = fg[cOff];
        if (Cb < 0)
          Cb += 127;
        else
          Cb -= 128;
        Cr = fg[cOff + 1];
        if (Cr < 0)
          Cr += 127;
        else
          Cr -= 128;
      }

      int R = Y + Cr + (Cr >> 2) + (Cr >> 3) + (Cr >> 5);
      if (R < 0)
        R = 0;
      else if (R > 255)
        R = 255;
      
      int G = Y - (Cb >> 2) + (Cb >> 4) + (Cb >> 5) - (Cr >> 1) + (Cr >> 3) + (Cr >> 4) + (Cr >> 5);
      if (G < 0)
        G = 0;
      else if (G > 255)
        G = 255;

      int B = Y + Cb + (Cb >> 1) + (Cb >> 2) + (Cb >> 6);
      if (B < 0)
        B = 0;
      else if (B > 255)
        B = 255;
        
      if (R < RGB_DARK_THRESHOLD && G < RGB_DARK_THRESHOLD && B < RGB_DARK_THRESHOLD)
        darkRegion++;

      out[i * hh + (hh - j - 1)] = 0xff000000 + (B << 16) + (G << 8) + R;
      pixPtr++;
    }
  }
  
  if (darkRegion >= (double)ns * 0.8)
    out[ns] = 1;
  else
    out[ns] = 0;
  
  jintArray jout = (*env)->NewIntArray(env, ns+1);
  (*env)->SetIntArrayRegion(env, jout, 0, ns+1, out);
  free(out);

  return jout;
}
