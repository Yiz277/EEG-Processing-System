package pitt.edu.publicGenerationSystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pitt.edu.publicGenerationSystem.entity.Warehouse;
import pitt.edu.publicGenerationSystem.repository.WarehouseRepository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class WarehouseService {
    @Autowired
    private WarehouseRepository warehouseRepository;

    // findAllLoadedWarehouses
    public List<Warehouse> findAllLoadedWarehouses() {
        return warehouseRepository.findAllLoadedWarehouses();
    }

    // findWarehousesById
    public Optional<Warehouse> findWarehouseById(Long id) {
        return warehouseRepository.findById(id);
    }

    @Transactional
    public Warehouse updateWarehouse(Warehouse warehouse) {
        Long id = warehouse.getId();
        if (id == null || !warehouseRepository.existsById(id)) {
            throw new RuntimeException("Warehouse with id " + id + " does not exist");
        }
        return warehouseRepository.save(warehouse);  // 保存并返回更新后的对象
    }
}
