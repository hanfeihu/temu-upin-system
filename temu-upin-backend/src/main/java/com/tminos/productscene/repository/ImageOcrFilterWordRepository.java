package com.tminos.productscene.repository;

import com.tminos.productscene.entity.ImageOcrFilterWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImageOcrFilterWordRepository extends JpaRepository<ImageOcrFilterWord, Long>, JpaSpecificationExecutor<ImageOcrFilterWord> {

    @Query("select w from ImageOcrFilterWord w order by w.id asc")
    List<ImageOcrFilterWord> findAllOrdered();
}
