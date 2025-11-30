package dev.chanler.researcher;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("dev.chanler.researcher.domain.mapper")
public class DeepResearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeepResearchApplication.class, args);
    }

}
