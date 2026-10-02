package br.com.taas.saas.gestaoproducao.operations.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "supabase.storage")
public class SupabaseStorageProperties {
    private String url = "";
    private String bucket = "";
    private String secretKey = "";

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public boolean configured() {
        return url != null && !url.isBlank()
                && bucket != null && bucket.matches("[a-z0-9][a-z0-9._-]{0,99}")
                && secretKey != null && !secretKey.isBlank();
    }
}
