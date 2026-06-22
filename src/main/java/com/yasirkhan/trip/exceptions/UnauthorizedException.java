package com.yasirkhan.trip.exceptions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnauthorizedException extends RuntimeException{

    private String message;
    private HttpStatus status;

    public UnauthorizedException(String message){
        super(message);
        this.message = message;
    }
}
