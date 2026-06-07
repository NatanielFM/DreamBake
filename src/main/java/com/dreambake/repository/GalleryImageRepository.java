package com.dreambake.repository;

import com.dreambake.entity.Category;
import com.dreambake.entity.GalleryImage;
import com.dreambake.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface GalleryImageRepository extends JpaRepository<GalleryImage, Long> {
}
