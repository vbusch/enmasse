package enmasse.controller.api.osb.v2;

import enmasse.controller.common.exceptionmapping.ErrorResponse;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

public class OSBExceptions {

    public static ConflictException conflictException(String message) {
        return new ConflictException(
                message,
                buildResponse(Response.Status.CONFLICT, "Conflict", message));
    }

    public static BadRequestException badRequestException(String message) {
        return new BadRequestException(
                message,
                buildResponse(Response.Status.BAD_REQUEST, "BadRequest", message));
    }

    public static NotFoundException notFoundException(String message) {
        return new NotFoundException(
                message,
                buildResponse(Response.Status.NOT_FOUND, "NotFound", message));
    }

    public static GoneException goneException(String message) {
        return new GoneException(
                message,
                buildResponse(Response.Status.GONE, "Gone", message));
    }

    public static UnprocessableEntityException unprocessableEntityException(String error, String message) {
        return new UnprocessableEntityException(
                message,
                buildResponse(UnprocessableEntityException.STATUS, error, message));
    }


    private static Response buildResponse(Response.Status status, String error, String message) {
        return Response.status(status)
                .entity(new ErrorResponse(error, message))
                .build();
    }

    private static Response buildResponse(int status, String error, String message) {
        return Response.status(status)
                .entity(new ErrorResponse(error, message))
                .build();
    }
}
