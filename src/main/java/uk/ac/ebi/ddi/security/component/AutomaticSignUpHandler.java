package uk.ac.ebi.ddi.security.component;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionSignUp;
import org.springframework.social.connect.UserProfile;
import org.springframework.social.facebook.api.Facebook;
import org.springframework.social.github.api.GitHub;
import org.springframework.social.twitter.api.Twitter;
import org.springframework.social.orcid.api.OrcidApi;
import org.springframework.social.orcid.jaxb.beans.OrcidProfile;
import org.springframework.social.orcid.jaxb.beans.PersonalDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.ac.ebi.ddi.security.model.DataSet;
import uk.ac.ebi.ddi.security.model.MongoUser;
import uk.ac.ebi.ddi.security.service.MongoUserDetailsService;

/*
* automatically create User with connection data
* */

@Component
public class AutomaticSignUpHandler implements ConnectionSignUp {

    private MongoUserDetailsService mongoUserDetailsService;

    @Autowired
    public AutomaticSignUpHandler(MongoUserDetailsService mongoUserDetailsService){
        this.mongoUserDetailsService = mongoUserDetailsService;
    }

    private volatile long userCount;

    @Override
    @Transactional
    public String execute(final Connection<?> connection) {
        //add new users to the db with its default roles for later use in SocialAuthenticationSuccessHandler
        final MongoUser user = new MongoUser();

        String imageUrl = connection.getImageUrl();

        UserProfile profile = connection.fetchUserProfile();
        String name = profile.getName();
        String url = connection.getProfileUrl();
        String affiliation = null;
        String bio = null;
        String orcid = null;
        String email = null;

        if(connection.getApi() instanceof Facebook ){
            email = ((Facebook)connection.getApi()).userOperations().getUserProfile().getEmail();
            affiliation = "Facebook user";
        }else if(connection.getApi() instanceof OrcidApi){

            OrcidApi orcidApi = (OrcidApi)connection.getApi();
            OrcidProfile orcidProfile = orcidApi.messageOperations().getOrcidProfile();
            PersonalDetails personalDetails = orcidProfile.getOrcidBio().getPersonalDetails();

            orcid = orcidProfile.getOrcidIdentifier().getPath();

            String givenName = personalDetails.getGivenNames();
            String familyName = personalDetails.getFamilyName();
            name = givenName + " " + familyName;

            imageUrl = "/assets/orcid.png";
            affiliation = "Orcid user";
            bio = orcidProfile.getOrcidBio().getBiography().getValue();
        }else if(connection.getApi() instanceof GitHub){
        	email = ((GitHub)connection.getApi()).userOperations().getUserProfile().getEmail();
            affiliation = "GitHub user";
        }else if(connection.getApi() instanceof Twitter){
        	//email = ((Twitter)connection.getApi()).userOperations().getUserProfile().getEmail();
            affiliation = "GitHub user";
        }

        user.setUserName(generateUniqueUserName(name));
        user.setImageUrl(imageUrl);
        user.setEmail(email);
        user.setHomepage(url);
        user.setAffiliation(affiliation);
        user.setBio(bio);
        user.setOrcid(orcid);

        //TODO: not needed?
        //user.setProviderId(connection.getKey().getProviderId());
        //user.setProviderUserId(connection.getKey().getProviderUserId());

        DataSet[] dataSets = new DataSet[]{ new DataSet("E-MTAB-12345","ArrayExpress","1999")
                                            ,new DataSet("X-FILES","IMDB","2017")};

        user.setDataSets( dataSets);

        user.setAccessToken(connection.createData().getAccessToken());

        mongoUserDetailsService.save(user);

        return user.getUserId();
    }

    private String generateUniqueUserName(final String firstName) {
        String username = firstName; //getUsernameFromFirstName - Full name
        String option = username;
        for (int i = 0; mongoUserDetailsService.findByUsername(option) != null; i++) {
            option = username + i;
        }
        return option;
    }

    private String getUsernameFromFirstName(final String userId) {
        final int max = 25;
        int index = userId.indexOf(' ');
        index = index == -1 || index > max ? userId.length() : index;
        index = index > max ? max : index;
        return userId.substring(0, index);
    }
}
