package com.phlox.tvwebbrowser.utils;



import android.net.Uri;

import com.brave.adblock.AdBlockClient;
import com.brave.adblock.Utils;
import com.tencent.smtt.export.external.interfaces.WebResourceRequest;

public class BraveForX5BridgeUtil extends Utils {
    public static AdBlockClient.FilterOption mapRequestToFilterOption(WebResourceRequest webResourceRequest) {
        String acceptHeader = (String)webResourceRequest.getRequestHeaders().get("Accept");
        if (acceptHeader != null) {
            if (acceptHeader.contains("image/")) {
                return AdBlockClient.FilterOption.IMAGE;
            }

            if (acceptHeader.contains("/css")) {
                return AdBlockClient.FilterOption.CSS;
            }

            if (acceptHeader.contains("javascript")) {
                return AdBlockClient.FilterOption.SCRIPT;
            }

            if (acceptHeader.contains("video/")) {
                return AdBlockClient.FilterOption.OBJECT;
            }
        }

        Uri url = webResourceRequest.getUrl();
        if (url != null) {
            if (uriHasExtension(url, "css")) {
                return AdBlockClient.FilterOption.CSS;
            }

            if (uriHasExtension(url, "js")) {
                return AdBlockClient.FilterOption.SCRIPT;
            }

            if (uriHasExtension(url, "png", "jpg", "jpeg", "webp", "svg", "gif", "bmp", "tiff")) {
                return AdBlockClient.FilterOption.IMAGE;
            }

            if (uriHasExtension(url, "mp4", "mov", "avi")) {
                return AdBlockClient.FilterOption.OBJECT;
            }
        }

        return AdBlockClient.FilterOption.UNKNOWN;
    }
    public static boolean uriHasExtension(Uri uri, String... values) {
        String path = uri.getPath();
        String[] var3 = values;
        int var4 = values.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            String ext = var3[var5];
            if (path.endsWith("." + ext)) {
                return true;
            }
        }

        return false;
    }

}
