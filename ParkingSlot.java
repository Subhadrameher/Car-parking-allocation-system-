package com.parkflow.model;import jakarta.persistence.*;
@Entity @Table(name="parking_slots") public class ParkingSlot{@Id public Integer id;public String status;protected ParkingSlot(){}public ParkingSlot(Integer i,String s){id=i;status=s;}}
