package com.ace.im.user.utils;


import cn.hutool.core.util.StrUtil;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
@Component
public class OssUtils {

    @Resource
    private MinioClient minioClient;

    @Value("${minio.url}")
    private String url;

    /**
     * 生成对象的临时上传地址。客户端可在有效期内向该地址发送 HTTP PUT 请求上传文件；本方法不上传文件。
     *
     * @param bucketName 目标存储桶名称
     * @param objectName 对象在存储桶中的名称或路径，例如 avatar/11.png
     * @param expires 上传地址的有效期，单位为秒
     * @return 带签名的临时上传地址
     */
    @SneakyThrows
    public String uploadUrl(String bucketName, String objectName, Integer expires) {

        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(expires, TimeUnit.SECONDS)
                        .build());
    }

    /**
     * 拼接对象的访问地址。本方法不下载文件，也不生成带签名的临时下载地址。
     *
     * @param bucketName 对象所在的存储桶名称
     * @param fileName 对象在存储桶中的名称或路径，应与上传时的 objectName 对应
     * @return 拼接后的访问地址；能否直接访问取决于存储桶的读取权限
     */
    public String downUrl(String bucketName, String fileName) {
        return url + StrUtil.SLASH + bucketName + StrUtil.SLASH + fileName;
    }
}
