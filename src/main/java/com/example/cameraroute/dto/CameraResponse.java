package com.example.cameraroute.dto;
public class CameraResponse {
    private String sbbm,sbmc,sbcs,xzqbm,xzqmc,pcsbm,pcsmc; private Double x,y; private double distance;
    public CameraResponse() {}
    public CameraResponse(String sbbm,String sbmc,String sbcs,Double x,Double y,String xzqbm,String xzqmc,String pcsbm,String pcsmc,double distance) {
        this.sbbm=sbbm;this.sbmc=sbmc;this.sbcs=sbcs;this.x=x;this.y=y;this.xzqbm=xzqbm;this.xzqmc=xzqmc;this.pcsbm=pcsbm;this.pcsmc=pcsmc;this.distance=distance;
    }
    public String getSbbm(){return sbbm;} public String getSbmc(){return sbmc;} public String getSbcs(){return sbcs;}
    public Double getX(){return x;} public Double getY(){return y;} public String getXzqbm(){return xzqbm;} public String getXzqmc(){return xzqmc;}
    public String getPcsbm(){return pcsbm;} public String getPcsmc(){return pcsmc;} public double getDistance(){return distance;}
    public void setSbbm(String v){sbbm=v;} public void setSbmc(String v){sbmc=v;} public void setSbcs(String v){sbcs=v;}
    public void setX(Double v){x=v;} public void setY(Double v){y=v;} public void setXzqbm(String v){xzqbm=v;} public void setXzqmc(String v){xzqmc=v;}
    public void setPcsbm(String v){pcsbm=v;} public void setPcsmc(String v){pcsmc=v;} public void setDistance(double v){distance=v;}
}