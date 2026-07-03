package com.ecommerce.product.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProductExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        pd.setTitle("Product Not Found");
        return pd;
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ProblemDetail handleCategoryNotFound(CategoryNotFoundException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        pd.setTitle("Category Not Found");
        return pd;
    }

    @ExceptionHandler(DuplicateCategoryNameException.class)
    public ProblemDetail handleDuplicateCategoryName(DuplicateCategoryNameException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        pd.setTitle("Duplicate Category Name");
        return pd;
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ProblemDetail handleCategoryInUse(CategoryInUseException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        pd.setTitle("Category In Use");
        return pd;
    }

    @ExceptionHandler(NotProductOwnerException.class)
    public ProblemDetail handleNotOwner(NotProductOwnerException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        pd.setTitle("Forbidden");
        return pd;
    }

    @ExceptionHandler(MissingSellerIdException.class)
    public ProblemDetail handleMissingSellerId(MissingSellerIdException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        pd.setTitle("Unauthorized");
        return pd;
    }
}
