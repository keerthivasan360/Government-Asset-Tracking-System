package gov.assets;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface LogRepository extends JpaRepository<TrackingLog,Long> {
    List<TrackingLog> findTop100ByOrderByTimestampDesc();
}
