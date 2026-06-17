package com.tminos.productscene.service;

import com.tminos.productscene.entity.ImageOcrFilterWord;
import com.tminos.productscene.repository.ImageOcrFilterWordRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ImageOcrFilterWordService {

    private final ImageOcrFilterWordRepository repo;

    public ImageOcrFilterWordService(ImageOcrFilterWordRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<ImageOcrFilterWord> list() {
        return repo.findAllOrdered();
    }

    @Transactional(readOnly = true)
    public Page<ImageOcrFilterWord> page(String q, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        String keyword = StringUtils.hasText(q) ? q.trim().toLowerCase() : null;
        if (!StringUtils.hasText(keyword)) {
            return repo.findAll(pageable);
        }
        return repo.findAll((root, query, cb) ->
                cb.like(cb.lower(root.get("word")), "%" + keyword + "%"), pageable);
    }

    @Transactional
    public ImageOcrFilterWord create(String word) {
        if (word == null || word.trim().isEmpty()) {
            throw new IllegalArgumentException("word is required");
        }
        ImageOcrFilterWord o = new ImageOcrFilterWord();
        o.setWord(word.trim());
        return repo.save(o);
    }

    @Transactional
    public ImageOcrFilterWord update(Long id, String word) {
        ImageOcrFilterWord o = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("ocr filter word not found: " + id));
        if (word == null || word.trim().isEmpty()) {
            throw new IllegalArgumentException("word is required");
        }
        o.setWord(word.trim());
        return repo.save(o);
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
