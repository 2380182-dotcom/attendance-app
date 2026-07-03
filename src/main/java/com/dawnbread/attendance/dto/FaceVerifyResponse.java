package com.dawnbread.attendance.dto;

public class FaceVerifyResponse {

    private boolean verified;
    private double similarity;
    private double threshold;
    private String verificationType;

    public FaceVerifyResponse() {}

    public FaceVerifyResponse(boolean verified, double similarity, double threshold, String verificationType) {
        this.verified = verified;
        this.similarity = similarity;
        this.threshold = threshold;
        this.verificationType = verificationType;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public String getVerificationType() {
        return verificationType;
    }

    public void setVerificationType(String verificationType) {
        this.verificationType = verificationType;
    }
}
