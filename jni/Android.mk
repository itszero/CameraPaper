LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_LDLIBS    := -L$(SYSROOT)/usr/lib -llog
LOCAL_MODULE    := camera-paper
LOCAL_SRC_FILES := camera-paper.c

include $(BUILD_SHARED_LIBRARY)
