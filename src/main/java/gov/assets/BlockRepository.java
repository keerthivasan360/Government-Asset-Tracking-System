package gov.assets;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {
    Optional<Block> findTopByOrderByBlockIndexDesc();
    List<Block> findAllByOrderByBlockIndexDesc();
    List<Block> findAllByOrderByBlockIndexAsc();
    List<Block> findByAssetIdOrderByBlockIndexDesc(Long assetId);
}
