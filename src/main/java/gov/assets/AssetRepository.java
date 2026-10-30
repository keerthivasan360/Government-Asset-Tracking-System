package gov.assets;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AssetRepository extends JpaRepository<Asset,Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Asset a where a.id = :id")
    java.util.Optional<Asset> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
