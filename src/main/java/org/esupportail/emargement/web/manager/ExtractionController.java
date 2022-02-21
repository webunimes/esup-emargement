package org.esupportail.emargement.web.manager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.naming.InvalidNameException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.esupportail.emargement.domain.*;
import org.esupportail.emargement.repositories.GroupeRepository;
import org.esupportail.emargement.repositories.SessionEpreuveRepository;
import org.esupportail.emargement.repositories.SessionLocationRepository;
import org.esupportail.emargement.repositories.TagCheckRepository;
import org.esupportail.emargement.services.*;
import org.esupportail.emargement.services.LogService.ACTION;
import org.esupportail.emargement.services.LogService.RETCODE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;

@Controller
@RequestMapping("/{emargementContext}")
@PreAuthorize(value="@userAppService.isAdmin() or @userAppService.isManager()")
public class ExtractionController {

	enum ExtractionType {apogee, ldap, csv, groupes};

	@Autowired(required = false)
	ApogeeService apogeeService;
	
	@Resource
	LogService logService;
	
	@Resource
	GroupeService groupeService;

	@Resource
	LdapService ldapService;

	@Resource
	LdapGroupService ldapGroupService;
	
	@Resource
	ImportExportService importExportService;
	
	@Resource
	TagCheckService tagCheckService;
	
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	@Autowired
	SessionEpreuveRepository sessionEpreuveRepository;
	
	@Autowired
	GroupeRepository groupeRepository;
	
	@Autowired
	SessionLocationRepository sessionLocationRepository;
	
	@Autowired
	TagCheckRepository tagCheckRepository;
	
	@Resource
	HelpService helpService;
	
	private final static String ITEM = "extraction";
	
	@ModelAttribute("active")
	public String getActiveMenu() {
		return ITEM;
	}

	@ModelAttribute("apogeeAvailable")
	public Boolean isApogeeAvailable() {
		return apogeeService != null;
	}
	
	@GetMapping(value = "/manager/extraction")
	public String index(Model model, @RequestParam(value = "type", required = false) ExtractionType type){
		if(type == null) {
			type = apogeeService != null ? ExtractionType.apogee : ExtractionType.ldap;
		}
		return redirectTab(model, type);
	}
	
	@RequestMapping(value = "/manager/extraction/tabs/{type}", produces = "text/html")
    public String redirectTab(Model uiModel, @PathVariable("type") ExtractionType type ) {
		uiModel.addAttribute("type", type.name());
		uiModel.addAttribute("help", helpService.getValueOfKey(ITEM));
		uiModel.addAttribute("allSessionEpreuves", importExportService.getNotFreeSessionEpreuve());
		if(ExtractionType.apogee.equals(type)) {
			uiModel.addAttribute("years", importExportService.getYearsUntilNow());
			if(apogeeService != null) {
				uiModel.addAttribute("allComposantes", apogeeService.getComposantes());
			} else {
				uiModel.addAttribute("allComposantes", new ArrayList<ApogeeBean>());
			}
		}else if(ExtractionType.groupes.equals(type)) {
			uiModel.addAttribute("allGroupes", groupeService.getNotEmptyGroupes());
		}
		return "manager/extraction/index";
	}
	
	@PostMapping(value = "/manager/extraction/search")
	public void search(@PathVariable String emargementContext,Model model, ApogeeBean apogeebean, HttpServletResponse response) throws IOException, CsvDataTypeMismatchException, CsvRequiredFieldEmptyException {
		if(apogeeService == null) {
			log.warn("Apogée non configurée");
			return;
		}
		try {
			List<ApogeeBean> inscrits = apogeeService.getListeFutursInscrits(apogeebean);
			String filename = "users.csv";

			response.setContentType("text/csv");
			response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
			        "attachment; filename=\"" + filename + "\"");

			//create a csv writer
			CSVWriter writer = new CSVWriter(response.getWriter()); 
			
			for(ApogeeBean inscrit : inscrits) {
				List <String> line = new ArrayList<String>();
				line.add(inscrit.getCodEtu());
				writer.writeNext(line.toArray(new String[1]));
			}
	        // closing writer connection 
	        writer.close(); 
			log.info("Extraction csv (apogee):  " + inscrits.size() + "résultats" );
			logService.log(ACTION.EXPORT_CSV, RETCODE.SUCCESS, "Extraction csv (apogee):" +  inscrits.size() + " résultats" , null,
							null, emargementContext, null);
		} catch (Exception e) {
			log.error("Erreur lors de l'extraction csv");
			logService.log(ACTION.EXPORT_CSV, RETCODE.FAILED, "Erreur lors de l'extraction csv (apogee)" , null, null, emargementContext, null);
			e.printStackTrace();
		}
	}
	
	@PostMapping(value = "/manager/extraction/csvFromLdap")
	public void csvFromLdap(@PathVariable String emargementContext,Model model, @RequestParam(value = "usersGroupLdap") List<String> usersGroupLdap, HttpServletResponse response) throws IOException, CsvDataTypeMismatchException, CsvRequiredFieldEmptyException {
		try {
			if(!usersGroupLdap.isEmpty()) {
				String filename = "users.csv";
	
				response.setContentType("text/csv");
				response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				        "attachment; filename=\"" + filename + "\"");
	
				//create a csv writer
				CSVWriter writer = new CSVWriter(response.getWriter()); 
				
				for(String inscrit : usersGroupLdap) {
					List <String> line = new ArrayList<String>();
					line.add(inscrit);
					writer.writeNext(line.toArray(new String[1]));
				}
		        // closing writer connection 
		        writer.close(); 
				log.info("Extraction csv (ldap):  " + usersGroupLdap.size() + "résultats" );
				logService.log(ACTION.EXPORT_CSV, RETCODE.SUCCESS, "Extraction csv (ldap):" +  usersGroupLdap.size() + " résultats" , null,
								null, emargementContext, null);
			}
		} catch (Exception e) {
			log.error("Erreur lors de l'extraction csv");
			logService.log(ACTION.EXPORT_CSV, RETCODE.FAILED, "Erreur lors de l'extraction csv (ldap)" , null, null, emargementContext, null);
			e.printStackTrace();
		}
	}
	
	@PostMapping(value = "/manager/extraction/csvFromGroupe")
	public void csvFromGroup(@PathVariable String emargementContext,Model model, @RequestParam(value = "groupe") List<Long> ids, HttpServletResponse response) throws IOException, CsvDataTypeMismatchException, CsvRequiredFieldEmptyException {
		try {
			if(!ids.isEmpty()) {
				String filename = "users.csv";
	
				response.setContentType("text/csv");
				response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				        "attachment; filename=\"" + filename + "\"");
	
				//create a csv writer
				CSVWriter writer = new CSVWriter(response.getWriter()); 
				
				List<Person> allPersons =new ArrayList<Person>();
				for(Long id : ids) {
					Groupe groupe = groupeRepository.findById(id).get();
					allPersons.addAll(groupe.getPersons());
				}
				
				for(Person person : allPersons) {
					List <String> line = new ArrayList<String>();
					line.add(person.getNumIdentifiant());
					writer.writeNext(line.toArray(new String[1]));
				}
		        // closing writer connection 
		        writer.close(); 
				log.info("Extraction csv (groupe) :  " + allPersons.size() + "résultats" );
				logService.log(ACTION.EXPORT_CSV, RETCODE.SUCCESS, "Extraction csv (groupe) :" +  allPersons.size() + " résultats" , null,
								null, emargementContext, null);
			}
		} catch (Exception e) {
			log.error("Erreur lors de l'extraction csv");
			logService.log(ACTION.EXPORT_CSV, RETCODE.FAILED, "Erreur lors de l'extraction csv (groupe)" , null, null, emargementContext, null);
			e.printStackTrace();
		}
	}
	
	@GetMapping("/manager/extraction/search/{param}")
    @ResponseBody
    public List<ApogeeBean> searchDiplomes(@PathVariable String param, ApogeeBean apogeeBean) {
		List<ApogeeBean> diplomes = new ArrayList<>();
		if(apogeeService != null) {
			HttpHeaders headers = new HttpHeaders();
			headers.add("Content-Type", "application/json; charset=utf-8");
			diplomes = apogeeService.searchList(param, apogeeBean);
		} else {
			log.warn("Apogée non configurée");
		}
        return diplomes;
    }
	
	@GetMapping("/manager/extraction/searchLocations")
    @ResponseBody
    public List<SessionLocation>  searchLocations(@RequestParam(value = "sessionEpreuveLdap", required = false) Long sessionEpreuveLdap, 
    						@RequestParam(value = "sessionEpreuveCsv", required = false) Long sessionEpreuveCsv,
    						@RequestParam(value = "sessionEpreuveGroupe", required = false) Long sessionEpreuveGroupe,
    						@RequestParam(value = "sessionEpreuve", required = false) Long sessionEpreuveApogee){
		Long seId = null;
		List<SessionLocation>  locations = new ArrayList<SessionLocation>();
		if(sessionEpreuveLdap != null) {
			seId = sessionEpreuveLdap;
		}else if(sessionEpreuveCsv != null) {
			seId = sessionEpreuveCsv;
		}else if(sessionEpreuveGroupe != null) {
			seId = sessionEpreuveGroupe;
		}else if(sessionEpreuveApogee != null) {
			seId = sessionEpreuveApogee;
		}
		if(seId != null) {
	    	HttpHeaders headers = new HttpHeaders();
			headers.add("Content-Type", "application/json; charset=utf-8");
			locations = sessionLocationRepository.findSessionLocationBySessionEpreuveId(seId);
		}
        return locations;
    }
    
	@GetMapping("/manager/extraction/countAutorises/{param}")
    @ResponseBody
    public int countNbEtudiants(@PathVariable String param, ApogeeBean apogeeBean) {
    	HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");
		int nbEtudiants = apogeeService.countAutorises(param, apogeeBean);
        return nbEtudiants;
    }

    @GetMapping("/manager/extraction/ldap/searchGroup")
    @ResponseBody
    public  List<String> searchGroupsLdap(@RequestParam("searchValue") String searchValue) throws InvalidNameException {
    	 List<String> ldapGroups = ldapGroupService.getAllGroupNames(searchValue);
    	return ldapGroups;
    }
    
    @GetMapping("/manager/extraction/ldap/searchUsers")
    public String searchUsersLdap(@PathVariable String emargementContext, Model uiModel,@RequestParam("searchGroup") String searchGroup) throws InvalidNameException {

		List<LdapUser> ldapMembers = ldapGroupService.getLdapMembers(searchGroup) ;
    	uiModel.addAttribute("group", searchGroup);
    	uiModel.addAttribute("ldapMembers", ldapMembers);
    	uiModel.addAttribute("allSessionEpreuves", sessionEpreuveRepository.findSessionEpreuveByIsSessionEpreuveClosedFalseOrderByDateExamen());
		if(apogeeService != null) {
			uiModel.addAttribute("allComposantes", apogeeService.getComposantes());
		} else {
			uiModel.addAttribute("allComposantes", new ArrayList<ApogeeBean>());
		}
		uiModel.addAttribute("years", importExportService.getYearsUntilNow());
		uiModel.addAttribute("help", helpService.getValueOfKey(ITEM));
		uiModel.addAttribute("size", helpService.getValueOfKey(ITEM));
		uiModel.addAttribute("type", "ldap");
    	return "manager/extraction/index";
    }
    
    @PostMapping(value = "/manager/extraction/importCsv", produces = "text/html")
    public String importCsv(@PathVariable String emargementContext, List<MultipartFile> files,  @RequestParam("sessionEpreuveCsv") Long id, @RequestParam(value= "sessionLocationCsv", required = false) Long slId,
    		Model uiModel, final RedirectAttributes redirectAttributes) throws Exception {
    	List<InputStream> streams = new ArrayList<InputStream>();
    	for(MultipartFile file : files) {
    		streams.add(file.getInputStream());
    	}
    	SequenceInputStream is = new SequenceInputStream(Collections.enumeration(streams));
    	List<Integer> bilanCsv =  tagCheckService.importTagCheckCsv(new InputStreamReader(is), null, id, emargementContext, null, true, null, slId, null);
    	redirectAttributes.addFlashAttribute("paramUrl", id);
    	redirectAttributes.addFlashAttribute("bilanCsv", bilanCsv);
    	return String.format("redirect:/%s/manager/extraction/tabs/csv", emargementContext);
    }
    
    @PostMapping("/manager/extraction/importFromApogee")
    @Transactional
    public String importFromApogee(@PathVariable String emargementContext, ApogeeBean apogeebean, BindingResult bindingResult, Model uiModel, HttpServletRequest httpServletRequest,  
    		@RequestParam(value = "sessionLocation", required = false) Long slId, final RedirectAttributes redirectAttributes) throws Exception {
        uiModel.asMap().clear();
        List<ApogeeBean> futursInscrits = apogeeService.getListeFutursInscrits(apogeebean);
        List<List<String>> finalList = apogeeService.getListeFutursInscritsDirectImport(futursInscrits);
        Map<String,String> mapEtapes = apogeeService.getMapEtapes(apogeebean, futursInscrits);
        List<Integer> bilanCsv =  tagCheckService.importTagCheckCsv(null, finalList, apogeebean.getSessionEpreuve().getId(), emargementContext, mapEtapes, true, null, slId, null);
    	redirectAttributes.addFlashAttribute("paramUrl", apogeebean.getSessionEpreuve().getId());
    	redirectAttributes.addFlashAttribute("bilanCsv", bilanCsv);
    	return String.format("redirect:/%s/manager/extraction/tabs/apogee", emargementContext);
    }
    
    @PostMapping("/manager/extraction/importFromLdap")
    @Transactional
    public String importFromLdap(@PathVariable String emargementContext, @RequestParam("sessionEpreuveLdap") Long id, @RequestParam(value = "usersGroupLdap") List<String> usersGroupLdap, 
    		@RequestParam(value="sessionLocationLdap", required = false) Long slId, Model uiModel, HttpServletRequest httpServletRequest, final RedirectAttributes redirectAttributes) throws Exception {
    	List<List<String>> finalList = tagCheckService.getListForimport(usersGroupLdap);
    	List<Integer> bilanCsv =  tagCheckService.importTagCheckCsv(null, finalList, id, emargementContext, null, true, null, slId, null);
    	redirectAttributes.addFlashAttribute("paramUrl", id);
    	redirectAttributes.addFlashAttribute("bilanCsv", bilanCsv);
    	return String.format("redirect:/%s/manager/extraction/tabs/ldap", emargementContext);
    }
    
    @PostMapping("/manager/extraction/importFromGroupe")
    @Transactional
    public String importFromGroupe(@PathVariable String emargementContext,  @RequestParam("sessionEpreuveGroupe") Long id, @RequestParam("groupe") List<Long> idGroupe,
    		@RequestParam(value="sessionLocationGroupe", required = false) Long slId, Model uiModel, HttpServletRequest httpServletRequest, final RedirectAttributes redirectAttributes) throws Exception {
    	List<String> usersGroupe = groupeService.getUsersForImport(idGroupe);
    	List<List<String>> finalList = tagCheckService.getListForimport(usersGroupe);
    	List<Integer> bilanCsv =  tagCheckService.importTagCheckCsv(null, finalList, id, emargementContext, null, true, null, slId, null);
    	redirectAttributes.addFlashAttribute("paramUrl", id);
    	redirectAttributes.addFlashAttribute("bilanCsv", bilanCsv);
    	return String.format("redirect:/%s/manager/extraction/tabs/groupes", emargementContext);
    }
}
