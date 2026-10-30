package gov.assets;
import jakarta.persistence.*;
@Entity @Table(name="users")
public class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(unique=true,nullable=false) public String username;
    public String password;
    public String role;
}
