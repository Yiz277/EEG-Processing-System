package pitt.edu.publicGenerationSystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pitt.edu.publicGenerationSystem.entity.Warehouse;
import java.util.List;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    @Query(value = "SELECT * FROM warehouses WHERE status = 'LOADED'", nativeQuery = true)
    List<Warehouse> findAllLoadedWarehouses();

    Warehouse findWarehousesById(Long warehouseId);

    @Query(value = "SELECT * FROM warehouses", nativeQuery = true)
    List<Warehouse> findAllWarehouses();
}
