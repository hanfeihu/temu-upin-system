package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuImageMeta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TemuImageMetaRepository extends JpaRepository<TemuImageMeta, Long> {

    Optional<TemuImageMeta> findByUrl(String url);

    List<TemuImageMeta> findByUrlIn(Collection<String> urls);
}
