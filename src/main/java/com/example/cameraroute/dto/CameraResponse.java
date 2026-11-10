package com.example.cameraroute.dto;

public class CameraResponse {
    private String sbbm;
    private String sbmc;
    private String sbcs;
    private Double x;
    private Double y;
    private String xzqbm;
    private String xzqmc;
    private String pcsbm;
    private String pcsmc;
    private double distance;

    public CameraResponse() { }

    public CameraResponse(String sbbm, String sbmc, String sbcs, Double x, Double y,
                          String xzqbm, String xzqmc, String pcsbm, String pcsmc,
                          double distance) {
        this.sbbm = sbbm;
        this.sbmc = sbmc;
        this.sbcs = sbcs;
        this.x = x;
        this.y = y;
        this.xzqbm = xzqbm;
        this.xzqmc = xzqmc;
        this.pcsbm = pcsbm;
        this.pcsmc = pcsmc;
        this.distance = distance;
    }

    public String getSbbm() { return sbbm; }
    public String getSbmc() { return sbmc; }
    public String getSbcs() { return sbcs; }
    public Double getX() { return x; }
    public Double getY() { return y; }
    public String getXzqbm() { return xzqbm; }
    public String getXzqmc() { return xzqmc; }
    public String getPcsbm() { return pcsbm; }
    public String getPcsmc() { return pcsmc; }
    public double getDistance() { return distance; }
    public void setSbbm(String sbbm) { this.sbbm = sbbm; }
    public void setSbmc(String sbmc) { this.sbmc = sbmc; }
    public void setSbcs(String sbcs) { this.sbcs = sbcs; }
    public void setX(Double x) { this.x = x; }
    public void setY(Double y) { this.y = y; }
    public void setXzqbm(String xzqbm) { this.xzqbm = xzqbm; }
    public void setXzqmc(String xzqmc) { this.xzqmc = xzqmc; }
    public void setPcsbm(String pcsbm) { this.pcsbm = pcsbm; }
    public void setPcsmc(String pcsmc) { this.pcsmc = pcsmc; }
    public void setDistance(double distance) { this.distance = distance; }
}
