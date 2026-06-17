package com.tminos.productscene.repository;

import com.tminos.productscene.entity.ImportTitleFilterWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportTitleFilterWordRepository extends JpaRepository<ImportTitleFilterWord, Long>, JpaSpecificationExecutor<ImportTitleFilterWord> {

    @Query("select w from ImportTitleFilterWord w order by w.id asc")
    List<ImportTitleFilterWord> findAllOrdered();
}
