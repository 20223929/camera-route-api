package com.example.cameraroute.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
}
