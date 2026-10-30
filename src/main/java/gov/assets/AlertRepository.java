package gov.assets;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AlertRepository extends JpaRepository<Alert,Long> {
    List<Alert> findByAssetIdAndResolvedFalse(Long id);
    List<Alert> findTop100ByOrderByCreatedAtDesc();
    long countByResolvedFalse();
}
