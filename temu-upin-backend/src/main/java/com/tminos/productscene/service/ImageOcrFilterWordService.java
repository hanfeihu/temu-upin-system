package com.tminos.productscene.service;

import com.tminos.productscene.entity.ImageOcrFilterWord;
import com.tminos.productscene.repository.ImageOcrFilterWordRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
