package com.yuelimin.controller;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 兼容 wanxin-p2p-web 原有的 /minio/image 上传约定: 返回 {flag, message, data}, data 为图片的浏览器访问地址.
 * 原服务用 AppId/AccessKey/SecretKey 请求头鉴权, 这里不校验.
 */
@Slf4j
@Tag(name = "ImageController", description = "图片上传(兼容万信前端)")
@RestController
@RequestMapping("/minio")
public class ImageController {
    private static final String PUBLIC_READ_POLICY = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
            + "\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::%s/*\"]}]}";

    @Resource
    private MinioClient minioClient;
    @Value("${minio.imageBucket:wanxin-image}")
    private String imageBucket;
    @Value("${minio.publicUrl:}")
    private String publicUrl;

    @Operation(summary = "上传图片")
    @PostMapping("/image")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        String name = file.getOriginalFilename();
        if (file.isEmpty() || name == null || !name.contains(".")) {
            return result(false, "文件为空或缺少扩展名", null);
        }
        String objectName = UUID.randomUUID().toString().replace("-", "") + name.substring(name.lastIndexOf("."));
        try (InputStream in = file.getInputStream()) {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder().bucket(imageBucket).object(objectName)
                    .stream(in, file.getSize(), -1).contentType(file.getContentType()).build());
        } catch (Exception e) {
            log.error("图片上传失败: {}", name, e);
            return result(false, "上传失败", null);
        }
        return result(true, "上传成功", publicUrl + "/" + imageBucket + "/" + objectName);
    }

    private void ensureBucket() throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(imageBucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(imageBucket).build());
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(imageBucket)
                    .config(String.format(PUBLIC_READ_POLICY, imageBucket)).build());
        }
    }

    private static Map<String, Object> result(boolean flag, String message, String data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("flag", flag);
        m.put("message", message);
        m.put("data", data);
        return m;
    }
}
