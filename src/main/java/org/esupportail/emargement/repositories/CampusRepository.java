package org.esupportail.emargement.repositories;

import java.util.List;

import org.esupportail.emargement.domain.Campus;
import org.esupportail.emargement.domain.Context;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CampusRepository extends JpaRepository<Campus, Long> {
	
	Long countBySite(String site);
	
	List<Campus> findByContext(Context context);
	
	List<Campus> findByOrderBySite();
	
	List<Campus> findBySite(String site);
	
	//STATS
	@Query(value = "select key, count(*) as count from campus, context where campus.context_id=context.id group by key order by key, count desc", nativeQuery = true)
	List<Object[]> countCampusesByContext();
	
    @Query(nativeQuery=true,value="SELECT key, COUNT(*) AS count FROM campus JOIN context ON context_id = context.id GROUP BY key ORDER BY key, count DESC") 
    List<Object[]> countByContext();

}
